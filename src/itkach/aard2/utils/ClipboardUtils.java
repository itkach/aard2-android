package itkach.aard2.utils;

import static android.content.Context.CLIPBOARD_SERVICE;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Log;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

public class ClipboardUtils {
    private final static Pattern[] NO_PASTE_PATTERNS = new Pattern[]{
            Patterns.WEB_URL,
            Patterns.EMAIL_ADDRESS,
            Patterns.PHONE
    };

    @Nullable
    public static CharSequence peek(@NonNull Context context) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
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

    @Nullable
    public static CharSequence take(@NonNull Context context) {
        CharSequence text = peek(context);
        ClipboardManager cm = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(null, ""));
        return text;
    }
}
