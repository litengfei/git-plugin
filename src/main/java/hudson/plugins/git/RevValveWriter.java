package hudson.plugins.git;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

/**
 * Only output change-logs before specified rev-commit appeared
 *
 * @author ltf
 * @since 2019/6/26, 上午10:25
 */
public class RevValveWriter extends GetFirstCommitWriter {

    /**
     * @param proxiedWriter Writer to be valved on
     */
    public RevValveWriter(Writer proxiedWriter) {
        super(proxiedWriter);
    }

    private boolean valveClosed = false;
    private ArrayList<RevTarget> targets = new ArrayList<>();

    public void addCloseValveRev(String closeValveRev) {
        if (closeValveRev != null)
            targets.add(new RevTarget(closeValveRev));
    }

    @Override
    synchronized public void write(char[] cbuf, int off, int len) throws IOException {
        if (valveClosed) return;
        else {
            for (RevTarget target : targets) {
                int idx = indexOf(cbuf, off, len,
                        target.target, target.targetOffset, target.targetCount,
                        COMMIT_PREFIX_LEN);
                if (idx >= COMMIT_PREFIX_LEN) {
                    valveClosed = isRevidInCommitSection(cbuf, off, idx);
                }
                if (valveClosed) {
                    super.write(cbuf, off, idx - COMMIT_PREFIX_LEN);
                    flush();
                    return;
                }
            }
        }

        super.write(cbuf, off, len);
    }

    //  rev-id appeared in parent section, current commit is not the commit with rev-id
    //    parent 40d65cf1da6307effe64dc0ceedb678f2443cb8e
    //
    //  rev-id appeared in commit section, current commit is the key commit we want
    //    commit 40d65cf1da6307effe64dc0ceedb678f2443cb8e
    //    commit 40d65cf1da6307effe64dc0ceedb678f2443cb8e (from 50183730c5662f7903b0e46bf984bc78614a54ee)
    private boolean isRevidInCommitSection(char[] source, int sourceOffset, int revIndex) {
        for (int i = 0; i < COMMIT_PREFIX_LEN; i++) {
            if (source[sourceOffset + revIndex - COMMIT_PREFIX_LEN + i] != COMMIT_PREFIX_CHARS[i]) break;
            return true;
        }
        return false;
    }

    private static class RevTarget {
        private final char[] target;
        private final int targetOffset;
        private final int targetCount;

        public RevTarget(String closeValveRev) {
            target = closeValveRev.toCharArray();
            targetCount = target.length;
            targetOffset = 0;
        }
    }
}
