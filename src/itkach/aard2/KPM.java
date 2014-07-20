package itkach.aard2;
/**
 * From http://stackoverflow.com/a/21341389
 */


public class KPM {
    /**
     * Search the data byte array for the first occurrence of the byte array pattern within given boundaries.
     * @param data
     * @param start First index in data
     * @param stop Last index in data so that stop-start = length
     * @param pattern What is being searched. '*' can be used as wildcard for "ANY character"
     * @return
     */
    public static int indexOf( byte[] data, int start, int stop, byte[] pattern) {
        if( data == null || pattern == null) return -1;

        int[] failure = computeFailure(pattern);

        int j = 0;

        for( int i = start; i < stop; i++) {
            while (j > 0 && ( pattern[j] != '*' && pattern[j] != data[i])) {
                j = failure[j - 1];
            }
            if (pattern[j] == '*' || pattern[j] == data[i]) {
                j++;
            }
            if (j == pattern.length) {
                return i - pattern.length + 1;
            }
        }
        return -1;
    }

    /**
     * Computes the failure function using a boot-strapping process,
     * where the pattern is matched against itself.
     */
    private static int[] computeFailure(byte[] pattern) {
        int[] failure = new int[pattern.length];

        int j = 0;
        for (int i = 1; i < pattern.length; i++) {
            while (j>0 && pattern[j] != pattern[i]) {
                j = failure[j - 1];
            }
            if (pattern[j] == pattern[i]) {
                j++;
            }
            failure[i] = j;
        }

        return failure;
    }
}
