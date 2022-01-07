package itkach.aard2;

import static android.content.Context.CLIPBOARD_SERVICE;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.util.Log;
import android.util.Patterns;

import java.util.regex.Pattern;

public class Clipboard {

    private final static Pattern[] NO_PASTE_PATTERNS = new Pattern[]{
            Patterns.WEB_URL,
            Patterns.EMAIL_ADDRESS,
            Patterns.PHONE
    };

    static CharSequence peek(Activity activity) {
        ClipboardManager cm = (ClipboardManager)activity.getSystemService(CLIPBOARD_SERVICE);
        ClipData clipData = cm.getPrimaryClip();
        if (clipData == null) {
            return null;
        }
        int count = clipData.getItemCount();
        for (int i = 0; i < count; i++) {
            ClipData.Item item = clipData.getItemAt(i);
            CharSequence text = item.getText();
            if (text != null && text.length() > 0) {
                for (Pattern p : NO_PASTE_PATTERNS) {
                    if (p.matcher(text).find()) {
                        Log.d("CLIPBOARD", "Text matched pattern " + p.pattern() + ", not pasting: " + text);
                        return null;
                    }
                }
                return text;
            }
        }
        return null;
    }

    static CharSequence take(Activity activity) {
        CharSequence text = peek(activity);
        ClipboardManager cm = (ClipboardManager)activity.getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(null, ""));
        return text;
    }
}
