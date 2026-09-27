package itkach.aard2;

// A RecyclerView adapter that observes an app-scoped data list (lookup results,
// dictionaries, bookmarks or history). Those lists outlive the views, so the
// adapter must be released - its data-set observer unregistered - when its owning
// view goes away; otherwise the list keeps the adapter, and through it the
// Activity and its view tree, alive across recreations.
interface ReleasableAdapter {
    void release();
}
