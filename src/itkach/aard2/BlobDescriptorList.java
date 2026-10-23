package itkach.aard2;

import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ibm.icu.text.Collator;
import com.ibm.icu.text.RuleBasedCollator;
import com.ibm.icu.text.StringSearch;

import java.text.StringCharacterIterator;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import itkach.slob.Slob;

final class BlobDescriptorList extends AbstractList<BlobDescriptor> {

    private final String TAG = getClass().getSimpleName();

    static enum SortOrder {
        TIME, NAME;
    }

    // The dictionaries entries point into.
    interface Dictionaries {
        // The loaded dictionary file with this id, or null.
        Slob getSlob(String slobId);
        // The loaded dictionary files with this uri, in list order: the
        // versions, volumes or other parts of one dictionary.
        List<Slob> findSlobsByURI(String uri);
        // Ids of the files in the dictionary list, including ones that failed
        // to load, and ones that a listed file replaced, overwritten in place,
        // whose entries are still to be moved to it (see
        // moveEntriesOfReplacedFile): until then they're its, not for the
        // move pass to move elsewhere.
        Set<String> getListedIds();
        // The loaded dictionary files.
        List<Slob> getLoadedSlobs();
    }

    private Dictionaries                    dictionaries;

    private DescriptorStore<BlobDescriptor> store;
    private List<BlobDescriptor>            list;
    private List<BlobDescriptor>            filteredList;
    private String                          filter;
    private SortOrder                       order;
    private boolean                         ascending;
    private final DataSetObservable         dataSetObservable;
    private Comparator<BlobDescriptor>      nameComparatorAsc;
    private Comparator<BlobDescriptor>      nameComparatorDesc;
    private Comparator<BlobDescriptor>      timeComparatorAsc;
    private Comparator<BlobDescriptor>      timeComparatorDesc;
    private Comparator<BlobDescriptor>      comparator;
    private Comparator<BlobDescriptor>      lastAccessComparator;
    private Slob.KeyComparator              keyComparator;
    private int                             maxSize;
    private RuleBasedCollator               filterCollator;
    private Handler                         handler;
    // True until loadAsync()'s results are merged in on the main thread and
    // entries are moved, and while a later move pass runs (see
    // moveToLoadedDictionaries). It starts out true: what waits for the
    // entries (see whenLoaded) can come before loadAsync(), e.g. the
    // dictionaries being loaded at startup. Read on the main thread only (set
    // false there too), so no synchronization.
    private boolean                         loading = true;
    // Set when dictionaries change while loading, so a move pass runs (again)
    // once that's done. Main thread only.
    private boolean                         moveAgain;
    // Actions waiting for loading to finish (see whenLoaded). Main thread only,
    // like loading itself.
    private final List<Runnable>            loadedCallbacks = new ArrayList<>();

    BlobDescriptorList(Dictionaries dictionaries, DescriptorStore<BlobDescriptor> store) {
        this(dictionaries, store, 1000);
    }

    BlobDescriptorList(Dictionaries dictionaries, DescriptorStore<BlobDescriptor> store, int maxSize) {
        this.dictionaries = dictionaries;
        this.store = store;
        this.maxSize = maxSize;
        this.list = new ArrayList<BlobDescriptor>();
        this.filteredList = new ArrayList<BlobDescriptor>();
        this.dataSetObservable = new DataSetObservable();
        this.filter = "";
        keyComparator = Slob.Strength.QUATERNARY.comparator;

        nameComparatorAsc = new Comparator<BlobDescriptor>() {
            @Override
            public int compare(BlobDescriptor b1, BlobDescriptor b2) {
            return keyComparator.compare(b1.key, b2.key);
            }
        };

        nameComparatorDesc = Collections.reverseOrder(nameComparatorAsc);

        timeComparatorAsc = new Comparator<BlobDescriptor>() {
            @Override
            public int compare(BlobDescriptor b1, BlobDescriptor b2) {
            return Util.compare(b1.createdAt, b2.createdAt);
            }
        };

        timeComparatorDesc = Collections.reverseOrder(timeComparatorAsc);

        lastAccessComparator = new Comparator<BlobDescriptor>() {
            @Override
            public int compare(BlobDescriptor b1, BlobDescriptor b2) {
                return  Util.compare(b2.lastAccess, b1.lastAccess);
            }
        };

        order = SortOrder.TIME;
        ascending = false;
        setSort(order, ascending);

        try {
            filterCollator = (RuleBasedCollator) Collator.getInstance(Locale.ROOT).clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        filterCollator.setStrength(Collator.PRIMARY);
        filterCollator.setAlternateHandlingShifted(false);
        handler = new Handler(Looper.getMainLooper());
    }

    public void registerDataSetObserver(DataSetObserver observer) {
        this.dataSetObservable.registerObserver(observer);
    }

    public void unregisterDataSetObserver(DataSetObserver observer) {
        this.dataSetObservable.unregisterObserver(observer);
    }

    /**
     * Notifies the attached observers that the underlying data has been changed
     * and any View reflecting the data set should refresh itself.
     */
    public void notifyDataSetChanged() {
        this.filteredList.clear();
        if (filter == null || filter.length() == 0) {
            this.filteredList.addAll(this.list);
        }
        else {
            for (BlobDescriptor bd : this.list) {
                StringSearch stringSearch = new StringSearch(
                        filter, new StringCharacterIterator(bd.key), filterCollator);
                int matchPos = stringSearch.first();
                if (matchPos != StringSearch.DONE) {
                    this.filteredList.add(bd);
                }
            }
        }
        sortOrderChanged();
    }

    private void sortOrderChanged() {
        Util.sort(this.filteredList, comparator);
        this.dataSetObservable.notifyChanged();
    }

    /**
     * Notifies the attached observers that the underlying data is no longer
     * valid or available. Once invoked this adapter is no longer valid and
     * should not report further data set changes.
     */
    public void notifyDataSetInvalidated() {
        this.dataSetObservable.notifyInvalidated();
    }

    boolean isLoading() {
        return loading;
    }

    // Run action once this list's entries are available: immediately if the
    // async load and any move have already finished, otherwise right after.
    // Lets a caller that needs a populated, up-to-date list - opening a
    // bookmarks/history article, resolving the bookmark icon - avoid acting on
    // the still-empty list during a cold start, or on entries about to be
    // moved. Main thread only.
    void whenLoaded(Runnable action) {
        if (loading) {
            loadedCallbacks.add(action);
        } else {
            action.run();
        }
    }

    // Load descriptors off the main thread. Reading and deserializing them from
    // disk can be slow (history fills to maxSize = 1000 entries), enough to drag
    // out cold start if done on it. isLoading() stays true from here until the
    // results are merged in on the main thread and entries are moved (see
    // moveToLoadedDictionaries); observers are notified at both ends, so a list
    // fragment can show its spinner meanwhile.
    // notifyDataSetChanged rebuilds and re-sorts the filtered view, so the merged
    // items land in the fragment's current sort order.
    void loadAsync() {
        loading = true;
        notifyDataSetChanged();
        Map<String, Slob> slobs = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        for (Slob slob : dictionaries.getLoadedSlobs()) {
            slobs.put(slob.getId().toString(), slob);
            labels.put(slob.getId().toString(), getLabel(slob));
        }
        Util.runAsync(() -> {
            List<BlobDescriptor> loaded = store.load(BlobDescriptor.class);
            // Entries without a label or a position get them here, and ones
            // with a blank uri the one their file goes by. That can be every
            // entry, too many to look up on the main thread, and none is
            // shared with it yet.
            for (BlobDescriptor bd : loaded) {
                boolean filledIn = fillInLabel(bd, labels.get(bd.slobId));
                filledIn |= fillInBlankUri(bd);
                Slob own = slobs.get(bd.slobId);
                if (own != null && bd.keyCount == null) {
                    filledIn |= locate(bd, own);
                }
                if (filledIn) {
                    store.saveLater(bd);
                }
            }
            return loaded;
        }, loaded -> {
            // Merge rather than addAll. An entry the user created while this was
            // loading - e.g. opening or bookmarking an article reached straight
            // from a link before the load finished - is already in the list, and
            // add() couldn't dedupe against it (it wasn't loaded yet), so it also
            // wrote its own store file. Keep that fresh copy and drop the stale
            // duplicate now read from disk. equals()/hashCode() are by article
            // identity, not the per-entry store id, so the set matches across the
            // two files (and catches any pre-existing duplicate files too).
            // Membership is via a HashSet: with up to maxSize entries a contains()
            // scan per loaded item would be quadratic. All of this runs on the
            // main thread, as do all list mutations, so the list is never touched
            // concurrently - the background task only reads the store and saves
            // entries not yet in the list.
            Set<BlobDescriptor> seen = new HashSet<>(list);
            for (BlobDescriptor bd : loaded) {
                if (seen.add(bd)) {
                    list.add(bd);
                } else {
                    store.deleteLater(bd.id);
                }
            }
            // The disk load plus any entries added while it was in flight can put
            // the list over the cap; trim back rather than leaving it to grow by
            // one on every such cold start.
            enforceMaxSize();
            startMoving();
        });
    }

    private void finishLoading() {
        loading = false;
        notifyDataSetChanged();
        // One at a time: a callback can start a move (see moveEntriesOf),
        // which counts as loading again, and the rest then wait for it, so
        // they see the moved entries and moves don't overlap.
        while (!loading && !loadedCallbacks.isEmpty()) {
            loadedCallbacks.remove(0).run();
        }
    }

    // An entry to move to another dictionary, and what looking its key up in
    // that dictionary's files found.
    private static final class Move {
        final BlobDescriptor bd;
        // The files to look the key up in, in list order.
        final List<Slob> to;
        // The file whose articles under the key come first when picking
        // which one the entry goes to (see pick), or null.
        final Slob preferred;
        Articles found;

        Move(BlobDescriptor bd, List<Slob> to, Slob preferred) {
            this.bd = bd;
            this.to = to;
            this.preferred = preferred;
        }
    }

    // The articles under a key in some dictionary files, and whether looking
    // it up failed in any of them (a read error): that file's are left out.
    private static final class Articles {
        final List<Slob.Blob> blobs = new ArrayList<>();
        boolean failed;
    }

    // Move entries whose dictionary file has been closed or replaced to the
    // loaded files with the same uri - a newer version of it, or its volumes:
    // the key is looked up in each, in list order, and the entry saved
    // pointing at the article picked (see pick). Entries for which none is
    // picked keep pointing at the old file, and record the files looked up
    // in, so the lookup isn't repeated until another file with the uri is
    // loaded. An entry whose lookup fails in any of the files (a read error)
    // isn't moved, since that file's articles could change the pick, nor
    // recorded, so it's retried on the next change or startup. An entry whose
    // file is still in the dictionary list but fails to load (e.g. on storage
    // that's currently unavailable) isn't moved, so it isn't bound to another
    // file for good.
    // Called whenever the set of loaded dictionaries changes; the lookups run in
    // the background, with the list counting as loading meanwhile, and a change
    // during a load or pass makes it run again once that's done.
    void moveToLoadedDictionaries() {
        if (loading) {
            moveAgain = true;
            return;
        }
        startMoving();
    }

    private void startMoving() {
        moveAgain = false;
        Set<String> listed = dictionaries.getListedIds();
        List<Move> moves = new ArrayList<>();
        for (BlobDescriptor bd : this.list) {
            Slob own = dictionaries.getSlob(bd.slobId);
            if (own != null) {
                // Gets the label of an entry whose dictionary wasn't loaded
                // when the entries were (see loadAsync), e.g. one on storage
                // that wasn't mounted yet. Its position is worked out on the
                // next load.
                if (fillInLabel(bd, getLabel(own))) {
                    store.saveLater(bd);
                }
                continue;
            }
            if (listed.contains(bd.slobId)) {
                continue;
            }
            // Looked up in every file with the uri once one is new to it: the
            // articles under its key are counted over all of them.
            List<Slob> files = replacementsOf(bd);
            for (Slob slob : files) {
                if (!wasNotFoundIn(bd, slob)) {
                    moves.add(new Move(bd, files, null));
                    break;
                }
            }
        }
        if (moves.isEmpty()) {
            if (loading) {
                finishLoading();
            }
            return;
        }
        runMoves(moves, true, null);
    }

    interface MoveDone {
        // tried: how many entries the dictionary had when the move ran (after
        // any pass that was running); moved: how many of them were moved.
        void onMoved(int tried, int moved);
    }

    // Like MoveDone, and failed: whether a lookup failed, leaving entries
    // that weren't moved for that (see applyMoves).
    private interface MovesDone {
        void onMoved(int tried, int moved, boolean failed);
    }

    // Moves every entry of a dictionary that's gone, going by the uri and file
    // id of one of its entries (see entriesOf), to the article with its key in
    // the dictionary with uri toUri, one the user picked. Entries for which no
    // article is picked there (see pick), or whose lookup failed, stay as they
    // are. done is called once they're moved.
    void moveEntriesOf(String uri, String slobId, String toUri, MoveDone done) {
        moveEntriesOf(uri, slobId, toUri, (tried, moved, failed) -> done.onMoved(tried, moved));
    }

    private void moveEntriesOf(String uri, String slobId, String toUri, MovesDone done) {
        whenLoaded(() -> {
            List<Slob> to = dictionaries.findSlobsByURI(toUri);
            List<Move> moves = new ArrayList<>();
            // Nothing is moved if a file with uri is loaded by now: the
            // dictionary isn't gone, so its entries belong with that file. The
            // move pass has taken them there, and the ones it didn't find
            // aren't to be moved elsewhere.
            boolean gone = uri == null || dictionaries.findSlobsByURI(uri).isEmpty();
            if (gone && !to.isEmpty()) {
                for (BlobDescriptor bd : entriesOf(uri, slobId)) {
                    moves.add(new Move(bd, to, null));
                }
            }
            if (moves.isEmpty()) {
                done.onMoved(0, 0, false);
                return;
            }
            runMoves(moves, false, done);
        });
    }

    // Moves the entries of the file replacedId, which went by replacedURI, to
    // the file toSlobId, which overwrote it in place. The user put the new
    // file in the old one's place, which says it's the same dictionary,
    // whatever the uris and whatever else is loaded: each key is looked up in
    // every file with the new file's uri, in list order (with a split
    // dictionary the article can be in another volume), with the new file's
    // articles first when picking which one an entry goes to. If the uri
    // changed, the entries left orphaned by earlier versions with the old one
    // move too, as the move pass would have moved them to a new version with
    // the same uri, unless a file with the old uri is still loaded: then they
    // belong with that file. done is called once they're moved, and not at
    // all if toSlobId isn't loaded by the time the move runs (its storage was
    // unmounted, or it was overwritten in turn), or a lookup failed: the move
    // is still to be done then, with the new file first, and is asked for
    // again on the next change.
    void moveEntriesOfReplacedFile(String replacedId, String replacedURI, String toSlobId,
                                   MoveDone done) {
        whenLoaded(() -> {
            Slob to = dictionaries.getSlob(toSlobId);
            if (to == null) {
                return;
            }
            String uri = to.getURI();
            List<Slob> files = dictionaries.findSlobsByURI(uri);
            List<Move> moves = new ArrayList<>();
            for (BlobDescriptor bd : this.list) {
                if (replacedId.equals(bd.slobId)) {
                    moves.add(new Move(bd, files, to));
                }
            }
            MovesDone then = (tried, moved, failed) -> {
                if (failed) {
                    return;
                }
                if (replacedURI.equals(uri)) {
                    done.onMoved(tried, moved);
                } else {
                    moveEntriesOf(replacedURI, null, uri,
                            (orphansTried, orphansMoved, orphansFailed) -> {
                                if (!orphansFailed) {
                                    done.onMoved(tried + orphansTried, moved + orphansMoved);
                                }
                            });
                }
            };
            if (moves.isEmpty()) {
                then.onMoved(0, 0, false);
            } else {
                runMoves(moves, true, then);
            }
        });
    }

    // Looks the keys of the moves up in the background, with the list counting
    // as loading meanwhile, then applies them. recordNotFound: whether an
    // entry for which no article is picked records that (see
    // BlobDescriptor.notFoundInIds).
    private void runMoves(List<Move> moves, boolean recordNotFound, MovesDone done) {
        if (!loading) {
            loading = true;
            notifyDataSetChanged();
        }
        Util.runAsync(() -> lookUp(moves), lookedUp -> {
            int moved = 0;
            if (lookedUp != null) {
                moved = applyMoves(moves, recordNotFound);
            }
            boolean failed = false;
            for (Move m : moves) {
                failed |= m.found == null || m.found.failed;
            }
            if (moveAgain) {
                startMoving();
            } else {
                finishLoading();
            }
            if (done != null) {
                done.onMoved(moves.size(), moved, failed);
            }
        });
    }

    private Boolean lookUp(List<Move> moves) {
        for (Move m : moves) {
            m.found = articlesUnder(m.bd.key, m.to);
        }
        return true;
    }

    // The articles under key in files, in that order: those with exactly the
    // key, or if none has it, those whose key is equal to it at quaternary
    // strength (not a prefix search, so there are only ever a few). In each
    // file they're one run of its sorted keys, in the order they're in there.
    private Articles articlesUnder(String key, List<Slob> files) {
        Articles exact = new Articles();
        List<Slob.Blob> matches = new ArrayList<>();
        for (Slob slob : files) {
            List<Slob.Blob> inFile = new ArrayList<>();
            try {
                Iterator<Slob.Blob> result = slob.find(key, Slob.Strength.QUATERNARY);
                while (result.hasNext()) {
                    inFile.add(result.next());
                }
            }
            catch (Exception ex) {
                exact.failed = true;
                Log.w(TAG, String.format("Failed to look up %s in %s (%s)",
                        key, slob.getId(), slob.fileURI), ex);
                continue;
            }
            for (Slob.Blob blob : inFile) {
                if (blob.key.equals(key)) {
                    exact.blobs.add(blob);
                }
            }
            matches.addAll(inFile);
        }
        if (exact.blobs.isEmpty()) {
            exact.blobs.addAll(matches);
        }
        return exact;
    }

    // Works out the position of bd, an entry of the loaded file own (see
    // BlobDescriptor.keyIndex). Returns whether it did: not if the lookup
    // failed.
    private boolean locate(BlobDescriptor bd, Slob own) {
        Articles found = articlesUnder(bd.key, Collections.singletonList(own));
        if (found.failed) {
            return false;
        }
        setPosition(bd, found.blobs);
        return true;
    }

    // Records the position of bd among blobs, the articles under its key in
    // its file.
    private static void setPosition(BlobDescriptor bd, List<Slob.Blob> blobs) {
        bd.keyCount = blobs.size();
        bd.keyIndex = null;
        for (int i = 0; i < blobs.size(); i++) {
            if (blobs.get(i).id.equals(bd.blobId)) {
                bd.keyIndex = i;
                break;
            }
        }
    }

    // Which of blobs, the articles under bd's key in the files of the
    // dictionary it's moving to, it's to point to, going by its position (see
    // BlobDescriptor.keyIndex), with preferred's articles first, if it's one
    // of the files, then the others in list order. Files are taken one at a
    // time, since the files with a uri can be versions loaded side by side,
    // not only volumes:
    // 1. in the first file with at least as many as there are in bd's, the
    //    one at its position, taking it that a new version adds articles
    //    after the ones already under the key;
    // 2. the article of the first file with only one.
    // An entry without a position goes to the first one. Null if there are
    // none, or several and the position doesn't tell which.
    private static Slob.Blob pick(BlobDescriptor bd, List<Slob.Blob> blobs, Slob preferred) {
        Map<Slob, List<Slob.Blob>> byFile = new LinkedHashMap<>();
        if (preferred != null) {
            byFile.put(preferred, new ArrayList<>());
        }
        for (Slob.Blob blob : blobs) {
            List<Slob.Blob> inFile = byFile.get(blob.owner);
            if (inFile == null) {
                inFile = new ArrayList<>();
                byFile.put(blob.owner, inFile);
            }
            inFile.add(blob);
        }
        List<Slob.Blob> all = new ArrayList<>();
        for (List<Slob.Blob> inFile : byFile.values()) {
            all.addAll(inFile);
        }
        if (all.isEmpty()) {
            return null;
        }
        if (bd.keyIndex == null || bd.keyCount == null) {
            return all.get(0);
        }
        int index = bd.keyIndex;
        int count = bd.keyCount;
        if (index >= count) {
            return null;
        }
        for (List<Slob.Blob> inFile : byFile.values()) {
            if (inFile.size() >= count) {
                return inFile.get(index);
            }
        }
        for (List<Slob.Blob> inFile : byFile.values()) {
            if (inFile.size() == 1) {
                return inFile.get(0);
            }
        }
        return null;
    }

    // Whether bd's key is recorded as not found in slob.
    private static boolean wasNotFoundIn(BlobDescriptor bd, Slob slob) {
        String slobId = slob.getId().toString();
        return slobId.equals(bd.notFoundIn)
                || (bd.notFoundInIds != null && bd.notFoundInIds.contains(slobId));
    }

    // Records that no article for bd was picked in the files tried (see
    // pick), nor in the ones it was already recorded not found in. Files no
    // longer loaded are left out, so the record doesn't grow with every
    // version; one added back gets looked up again.
    private void recordNotFound(BlobDescriptor bd, List<Slob> tried) {
        List<String> ids = new ArrayList<>();
        for (Slob slob : replacementsOf(bd)) {
            if (tried.contains(slob) || wasNotFoundIn(bd, slob)) {
                ids.add(slob.getId().toString());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        bd.notFoundInIds = ids;
        bd.notFoundIn = null;
        store.saveLater(bd);
    }

    // Returns how many entries were moved.
    private int applyMoves(List<Move> moves, boolean recordNotFound) {
        Set<BlobDescriptor> current = Collections.newSetFromMap(new IdentityHashMap<>());
        current.addAll(this.list);
        Set<BlobDescriptor> moved = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Move m : moves) {
            BlobDescriptor bd = m.bd;
            // Skip entries removed while the lookups ran, and files closed
            // meanwhile: that change runs the pass again.
            if (!current.contains(bd)) {
                continue;
            }
            if (m.found.failed) {
                continue;
            }
            Slob.Blob found = pick(bd, m.found.blobs, m.preferred);
            if (found == null) {
                if (recordNotFound) {
                    recordNotFound(bd, m.to);
                }
                continue;
            }
            Slob to = found.owner;
            String toId = to.getId().toString();
            if (dictionaries.getSlob(toId) == null) {
                continue;
            }
            bd.slobId = toId;
            bd.slobUri = to.getURI();
            bd.slobLabel = getLabel(to);
            bd.blobId = found.id;
            bd.key = found.key;
            bd.notFoundInIds = null;
            bd.notFoundIn = null;
            List<Slob.Blob> inFile = new ArrayList<>();
            for (Slob.Blob blob : m.found.blobs) {
                if (blob.owner.equals(to)) {
                    inFile.add(blob);
                }
            }
            setPosition(bd, inFile);
            moved.add(bd);
        }
        if (moved.isEmpty()) {
            return 0;
        }
        // A moved entry can now be equal to one already in the list (the same
        // article bookmarked again in the new version) or to another moved one.
        // Keep the one already there, with the earlier creation time (shown, and
        // sorted on) and the later access time (decides what's evicted first).
        Map<BlobDescriptor, BlobDescriptor> kept = new HashMap<>();
        for (BlobDescriptor bd : this.list) {
            if (!moved.contains(bd) && !kept.containsKey(bd)) {
                kept.put(bd, bd);
            }
        }
        Set<BlobDescriptor> dropped = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlobDescriptor bd : moved) {
            BlobDescriptor existing = kept.get(bd);
            if (existing == null) {
                kept.put(bd, bd);
                store.saveLater(bd);
                continue;
            }
            existing.createdAt = Math.min(existing.createdAt, bd.createdAt);
            existing.lastAccess = Math.max(existing.lastAccess, bd.lastAccess);
            store.saveLater(existing);
            store.deleteLater(bd.id);
            dropped.add(bd);
        }
        this.list.removeAll(dropped);
        Log.d(TAG, String.format("Moved %d entries, dropped %d duplicates",
                moved.size(), dropped.size()));
        return moved.size();
    }

    private void doUpdateLastAccess(BlobDescriptor bd) {
        // A pager can still show an entry that has since been removed; saving
        // it would bring it back.
        if (!isListed(bd)) {
            return;
        }
        long t = System.currentTimeMillis();
        long dt = t - bd.lastAccess;
        if (dt < 2000) {
            return;
        }
        bd.lastAccess = t;
        store.saveLater(bd);
    }

    private boolean isListed(BlobDescriptor bd) {
        return indexOfListed(bd) > -1;
    }

    // Index of this very entry, not of one equal to it: the filtered view can
    // briefly hold an entry a move pass has merged into another, equal one.
    private int indexOfListed(BlobDescriptor bd) {
        for (int i = 0; i < this.list.size(); i++) {
            if (this.list.get(i) == bd) {
                return i;
            }
        }
        return -1;
    }

    void updateLastAccess(final BlobDescriptor bd) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            doUpdateLastAccess(bd);
        }
        else {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    doUpdateLastAccess(bd);
                }
            });
        }
    }

    // The entry's dictionary, or null if its file isn't loaded.
    Slob resolveOwner(BlobDescriptor bd) {
        return dictionaries.getSlob(bd.slobId);
    }

    // Why an entry's article can't be shown, if it can't. The same rules
    // decide what the move pass does with it (see moveToLoadedDictionaries).
    enum OrphanState {
        // Its dictionary file is loaded.
        NOT_ORPHANED,
        // Its file is in the dictionary list but failed to load (e.g. it's on
        // storage that isn't mounted), so it may well come back.
        UNAVAILABLE,
        // Its file was closed or replaced, and files with its uri, a newer
        // version of it or its volumes, are loaded (see replacementsOf). The
        // entry wasn't moved there: its key isn't in them, or a lookup failed
        // and is retried on the next change.
        REPLACED,
        // Its file was closed, and no file with its uri is loaded.
        GONE
    }

    OrphanState orphanState(BlobDescriptor bd) {
        if (dictionaries.getSlob(bd.slobId) != null) {
            return OrphanState.NOT_ORPHANED;
        }
        if (dictionaries.getListedIds().contains(bd.slobId)) {
            return OrphanState.UNAVAILABLE;
        }
        return replacementsOf(bd).isEmpty() ? OrphanState.GONE : OrphanState.REPLACED;
    }

    // For an entry whose dictionary file isn't loaded: the loaded files with
    // its uri, in list order.
    List<Slob> replacementsOf(BlobDescriptor bd) {
        return bd.slobUri == null
                ? Collections.emptyList() : dictionaries.findSlobsByURI(bd.slobUri);
    }

    // The entry's article, or null if its dictionary file isn't loaded. Entries
    // are moved to newer versions of their dictionary before the list counts as
    // loaded (see moveToLoadedDictionaries), so there's nothing to look up here.
    Slob.Blob resolve(BlobDescriptor bd) {
        Slob slob = resolveOwner(bd);
        if (slob == null) {
            return null;
        }
        updateLastAccess(bd);
        return new Slob.Blob(slob, bd.blobId, bd.key, bd.fragment);
    }

    // The entry for the article at contentUrl, or null if the url isn't an
    // article's or its dictionary file isn't loaded (e.g. an article left open
    // in another window while its dictionary was closed).
    public BlobDescriptor createDescriptor(String contentUrl) {
        Log.d(TAG, "Create descriptor from content url: " + contentUrl);
        Uri uri = Uri.parse(contentUrl);
        BlobDescriptor bd = BlobDescriptor.fromUri(uri);
        if (bd == null) {
            return null;
        }
        Slob slob = dictionaries.getSlob(bd.slobId);
        if (slob == null) {
            return null;
        }
        bd.slobUri = slob.getURI();
        bd.slobLabel = getLabel(slob);
        return bd;
    }

    // The dictionary's label, or null if it has none (blank counts as none).
    static String getLabel(Slob slob) {
        String label = slob.getTags().get("label");
        return Util.isBlank(label) ? null : label;
    }

    // Gives an entry without a label this one, if there is one. Returns
    // whether it did.
    private static boolean fillInLabel(BlobDescriptor bd, String label) {
        if (bd.slobLabel != null || label == null) {
            return false;
        }
        bd.slobLabel = label;
        return true;
    }

    // An entry saved from a dictionary whose uri tag is blank has that blank
    // uri, which every such dictionary would share. Gives it the uri its file
    // goes by (see Slob.getURI), made from the file's id, whether or not the
    // file is loaded. Returns whether it did.
    private static boolean fillInBlankUri(BlobDescriptor bd) {
        if (bd.slobUri == null || !Util.isBlank(bd.slobUri)) {
            return false;
        }
        bd.slobUri = "slob:" + bd.slobId;
        return true;
    }

    // Add an entry for the article at contentUrl, or return the existing one.
    // Returns null, adding nothing, when there can't be an entry for it (see
    // createDescriptor).
    public BlobDescriptor add(String contentUrl) {
        BlobDescriptor bd = createDescriptor(contentUrl);
        if (bd == null) {
            return null;
        }
        int index = this.list.indexOf(bd);
        if (index > -1) {
            return this.list.get(index);
        }
        // One lookup, on the main thread: a failed one is retried when the
        // entries are next loaded (see loadAsync).
        locate(bd, dictionaries.getSlob(bd.slobId));
        this.list.add(bd);
        store.saveLater(bd);
        enforceMaxSize();
        notifyDataSetChanged();
        return bd;
    }

    // Evict least-recently-accessed entries until the list is back within maxSize.
    // add() overshoots by one at a time; a merge (loadAsync) can overshoot by many
    // when entries were added while the disk load was in flight - so loop, sorting
    // once, rather than dropping a single entry.
    private void enforceMaxSize() {
        if (this.list.size() <= this.maxSize) {
            return;
        }
        Util.sort(this.list, lastAccessComparator);
        while (this.list.size() > this.maxSize) {
            BlobDescriptor lru = this.list.remove(this.list.size() - 1);
            store.deleteLater(lru.id);
        }
    }

    public BlobDescriptor remove(String contentUrl) {
        int index = this.list.indexOf(createDescriptor(contentUrl));
        if (index > -1) {
            return removeByIndex(index);
        }
        return null;
    }

    public BlobDescriptor remove(int index) {
        int realIndex = indexOfListed(this.filteredList.get(index));
        if (realIndex > -1) {
            return removeByIndex(realIndex);
        }
        return null;
    }

    // Re-insert a descriptor removed via remove(int) - the undo of a swipe-remove.
    // Keeps the original object (and its timestamp), so it re-sorts back into its
    // natural place rather than jumping to the top like a fresh add would.
    public void restore(BlobDescriptor bd) {
        this.list.add(bd);
        store.saveLater(bd);
        notifyDataSetChanged();
    }

    private BlobDescriptor removeByIndex(int index) {
        BlobDescriptor bd = this.list.remove(index);
        if (bd != null) {
            store.deleteLater(bd.id);
            notifyDataSetChanged();
        }
        return bd;
    }

    // Removes this very entry (not one equal to it). Returns whether it was
    // listed.
    boolean removeEntry(BlobDescriptor bd) {
        int index = indexOfListed(bd);
        if (index < 0) {
            return false;
        }
        removeByIndex(index);
        return true;
    }

    // The entries of a dictionary that's gone (see OrphanState.GONE), going by
    // one of them: those with its uri whose file isn't in the dictionary list,
    // so those of every file the dictionary was made of, or, without a uri,
    // those of its file. An entry whose file is listed but fails to load is
    // left out, so it isn't moved or removed for a passing problem.
    private List<BlobDescriptor> entriesOf(String uri, String slobId) {
        Set<String> listed = dictionaries.getListedIds();
        List<BlobDescriptor> result = new ArrayList<>();
        for (BlobDescriptor bd : this.list) {
            boolean same = uri == null ? slobId.equals(bd.slobId) : uri.equals(bd.slobUri);
            if (same && !listed.contains(bd.slobId)) {
                result.add(bd);
            }
        }
        return result;
    }

    // Number of entries of orphaned's dictionary, one that's gone (see
    // entriesOf).
    int countEntriesOf(BlobDescriptor orphaned) {
        return entriesOf(orphaned.slobUri, orphaned.slobId).size();
    }

    // Removes every entry of orphaned's dictionary, one that's gone (see
    // entriesOf). Returns how many.
    int removeEntriesOf(BlobDescriptor orphaned) {
        Set<BlobDescriptor> removed = Collections.newSetFromMap(new IdentityHashMap<>());
        removed.addAll(entriesOf(orphaned.slobUri, orphaned.slobId));
        for (BlobDescriptor bd : removed) {
            store.deleteLater(bd.id);
        }
        this.list.removeAll(removed);
        if (!removed.isEmpty()) {
            notifyDataSetChanged();
        }
        return removed.size();
    }

    // Delete every entry, from both the in-memory list and the backing store.
    // Used when history recording is turned off.
    public void clear() {
        for (BlobDescriptor bd : this.list) {
            store.deleteLater(bd.id);
        }
        this.list.clear();
        notifyDataSetChanged();
    }

    @Override
    public boolean isEmpty() {
        // The true count, independent of any active filter (AbstractList's
        // default isEmpty() reflects the filtered view - size() below).
        return this.list.isEmpty();
    }

    public boolean contains(String contentUrl) {
        return this.list.contains(createDescriptor(contentUrl));
    }

    public void setFilter(String filter) {
        this.filter = filter;
        notifyDataSetChanged();
    }

    public String getFilter() {
        return this.filter;
    }

    @Override
    public BlobDescriptor get(int location) {
        return this.filteredList.get(location);
    }

    @Override
    public int size() {
        return this.filteredList.size();
    }

    public void setSort(boolean ascending) {
        setSort(this.order, ascending);
    }

    public void setSort(SortOrder order) {
        setSort(order, this.ascending);
    }

    public SortOrder getSortOrder() {
        return this.order;
    }

    public boolean isAscending() {
        return this.ascending;
    }

    public void setSort(SortOrder order, boolean ascending) {
        this.order = order;
        this.ascending = ascending;
        Comparator<BlobDescriptor> c = null;
        if (order == SortOrder.NAME) {
            c = ascending ? nameComparatorAsc : nameComparatorDesc;
        }
        if (order == SortOrder.TIME) {
            c = ascending ? timeComparatorAsc : timeComparatorDesc;
        }
        if (c != comparator) {
            comparator = c;
            sortOrderChanged();
        }
    }

}
