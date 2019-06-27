package hudson.plugins.git;

import java.io.IOException;
import java.io.Writer;

/**
 * Get First Commit Id in the output change-logs
 *
 * @author ltf
 * @since 2019/6/27, 上午10:59
 */
public class GetFirstCommitWriter extends Writer {

    protected final static String COMMIT_PREFIX = "commit ";
    protected final static char[] COMMIT_PREFIX_CHARS = COMMIT_PREFIX.toCharArray();
    protected final static int COMMIT_PREFIX_LEN = COMMIT_PREFIX.length();
    private final static int REV_CHARS_LEN = 40;

    public GetFirstCommitWriter(Writer proxiedWriter) {
        this.proxiedWriter = proxiedWriter;
    }

    private final Writer proxiedWriter;

    public String getFirstCommitRev() {
        return firstCommitRev;
    }

    private String firstCommitRev;

    @Override
    synchronized public void write(char[] cbuf, int off, int len) throws IOException {
        proxiedWriter.write(cbuf, off, len);
        if (firstCommitRev == null) {
            firstCommitRev = getCommitRev(cbuf, off, len);
        }
    }

    @Override
    public void flush() throws IOException {
        proxiedWriter.flush();
    }

    @Override
    public void close() throws IOException {
        proxiedWriter.close();
    }

    /**
     * get commit Revision if it's a commit line data
     *
     * @return the commit Revision, or null if it's not a commit line data
     */
    private String getCommitRev(char[] cbuf, int off, int len) {
        int idx = indexOf(cbuf, off, len, COMMIT_PREFIX_CHARS, 0, COMMIT_PREFIX_LEN, 0);
        if (idx > -1) {
            int crPos = idx + COMMIT_PREFIX_LEN + REV_CHARS_LEN;
            if (crPos < len && cbuf[off + crPos] == '\n') {
                return String.copyValueOf(cbuf, off + idx + COMMIT_PREFIX_LEN, REV_CHARS_LEN);
            }
        }
        return null;
    }


    protected static int indexOf(char[] source, int sourceOffset, int sourceCount,
                                 char[] target, int targetOffset, int targetCount,
                                 int fromIndex) {
        if (fromIndex >= sourceCount) {
            return (targetCount == 0 ? sourceCount : -1);
        }

        char first = target[targetOffset];
        int max = sourceOffset + (sourceCount - targetCount);

        for (int i = sourceOffset + fromIndex; i <= max; i++) {
            /* Look for first character. */
            if (source[i] != first) {
                while (++i <= max && source[i] != first) ;
            }

            /* Found first character, now look at the rest of v2 */
            if (i <= max) {
                int j = i + 1;
                int end = j + targetCount - 1;
                for (int k = targetOffset + 1; j < end && source[j]
                        == target[k]; j++, k++)
                    ;

                if (j == end) {
                    /* Found whole string. */
                    return i - sourceOffset;
                }
            }
        }
        return -1;
    }
}
