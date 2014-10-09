package itkach.aard2;

import android.util.Log;
import android.webkit.JavascriptInterface;

/**
* Created by itkach on 10/4/14.
*/
public class StyleTitlesReceiver {

    String[] titles;

    private final String[] DEFAULT;

    StyleTitlesReceiver(String defaultStyle) {
        DEFAULT = new String[]{defaultStyle};
    }

    @JavascriptInterface
    public void setTitles(String[] titles) {
        Log.d(getClass().getName(),
                String.format("Got %d style titles", titles.length));
        this.titles = concat(DEFAULT, titles);
        for (String title : titles) {
            Log.d(getClass().getName(), title);
        }
    }

    String[] concat(String[] A, String[] B) {
        int aLen = A.length;
        int bLen = B.length;
        String[] C= new String[aLen+bLen];
        System.arraycopy(A, 0, C, 0, aLen);
        System.arraycopy(B, 0, C, aLen, bLen);
        return C;
    }

}
