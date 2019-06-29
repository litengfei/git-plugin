package hudson.plugins.git;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import org.apache.commons.lang.time.FastDateFormat;
import org.codehaus.groovy.runtime.StringBufferWriter;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;

/**
 * Get all branches change log for last 24 hours
 *
 * @author ltf
 * @since 2019/6/27, 下午4:23
 */
public class AllBranchesChangelog {
    public static final int TIME_WINDOW = 60 * 60 * 24;


    public static void getAllBranchesChangelog(final GitClient git, FilePath changelogFile) throws IOException, InterruptedException {
        StringBuffer changeLogs = git.withRepository(new RepositoryCallback<StringBuffer>() {
            @Override
            public StringBuffer invoke(Repository repo, VirtualChannel channel) throws IOException, InterruptedException {
                ObjectReader or = repo.newObjectReader();
                RevWalk walk = new RevWalk(or);

                for (Branch b : git.getBranches()) {
                    walk.markStart(walk.lookupCommit(b.getSHA1()));
                }
                StringBuffer changeLogs = new StringBuffer();
                try (PrintWriter pw = new PrintWriter(new StringBufferWriter(changeLogs))) {
                    RawFormatter formatter = new RawFormatter();
                    int startTime = (int) (System.currentTimeMillis() / 1000) - TIME_WINDOW;
                    for (RevCommit commit : walk) {
                        // git whatachanged doesn't show the merge commits unless -m is given
                        if (commit.getParentCount() > 1) continue;
                        if (commit.getCommitTime() < startTime) break;
                        formatter.format(repo, commit, null, pw, true);
                    }
                } catch (IOException e) {
                    throw new GitException("Error: jgit whatchanged when get all branches log " + e.getMessage(), e);
                } finally {
                    walk.close();
                    or.close();
                    repo.close();
                }
                return changeLogs;
            }
        });
        FilePath fp = new FilePath(changelogFile.getParent(), "all-branches-changelog.xml");
        OutputStreamWriter out = new OutputStreamWriter(fp.write(), "UTF-8");
        out.write(changeLogs.toString());
        out.flush();
        out.close();
    }


    /**
     * Formats {@link RevCommit}.
     */
    private static class RawFormatter {
        private boolean hasNewPath(DiffEntry d) {
            return d.getChangeType() == DiffEntry.ChangeType.COPY || d.getChangeType() == DiffEntry.ChangeType.RENAME;
        }

        private String statusOf(DiffEntry d) {
            switch (d.getChangeType()) {
                case ADD:
                    return "A";
                case MODIFY:
                    return "M";
                case DELETE:
                    return "D";
                case RENAME:
                    return "R" + d.getScore();
                case COPY:
                    return "C" + d.getScore();
                default:
                    throw new AssertionError("Unexpected change type: " + d.getChangeType());
            }
        }

        public static final String ISO_8601 = "yyyy-MM-dd'T'HH:mm:ssZ";

        /**
         * Formats a commit into the raw format.
         *
         * @param commit Commit to format.
         * @param parent Optional parent commit to produce the diff against. This only matters
         *               for merge commits, and git-log/git-whatchanged/etc behaves differently with respect to this.
         */
        @SuppressFBWarnings(value = "VA_FORMAT_STRING_USES_NEWLINE",
                justification = "Windows git implementation requires specific line termination")
        public void format(final Repository repo, RevCommit commit, @Nullable RevCommit parent, PrintWriter pw, Boolean useRawOutput) throws IOException {
            if (parent != null)
                pw.printf("commit %s (from %s)\n", commit.name(), parent.name());
            else
                pw.printf("commit %s\n", commit.name());

            pw.printf("tree %s\n", commit.getTree().name());
            for (RevCommit p : commit.getParents())
                pw.printf("parent %s\n", p.name());
            FastDateFormat iso = FastDateFormat.getInstance(ISO_8601);
            PersonIdent a = commit.getAuthorIdent();
            pw.printf("author %s <%s> %s\n", a.getName(), a.getEmailAddress(), iso.format(a.getWhen()));
            PersonIdent c = commit.getCommitterIdent();
            pw.printf("committer %s <%s> %s\n", c.getName(), c.getEmailAddress(), iso.format(c.getWhen()));

            // indent commit messages by 4 chars
            String msg = commit.getFullMessage();
            if (msg.endsWith("\n")) msg = msg.substring(0, msg.length() - 1);
            msg = msg.replace("\n", "\n    ");
            msg = "\n    " + msg + "\n";

            pw.println(msg);

            // see man git-diff-tree for the format
            try (ObjectReader or = repo.newObjectReader();
                 TreeWalk tw = new TreeWalk(or)) {
                if (parent != null) {
                    /* Caller provided a parent commit, use it */
                    tw.reset(parent.getTree(), commit.getTree());
                } else {
                    if (commit.getParentCount() > 0) {
                        /* Caller failed to provide parent, but a parent
                         * is available, so use the parent in the walk
                         */
                        tw.reset(commit.getParent(0).getTree(), commit.getTree());
                    } else {
                        /* First commit in repo has 0 parent count, but
                         * the TreeWalk requires exactly two nodes for its
                         * walk.  Use the same node twice to satisfy
                         * TreeWalk. See JENKINS-22343 for details.
                         */
                        tw.reset(commit.getTree(), commit.getTree());
                    }
                }
                tw.setRecursive(true);
                tw.setFilter(TreeFilter.ANY_DIFF);

                final RenameDetector rd = new RenameDetector(repo);

                rd.reset();
                rd.addAll(DiffEntry.scan(tw));
                List<DiffEntry> diffs = rd.compute(or, null);
                if (useRawOutput) {
                    for (DiffEntry diff : diffs) {
                        pw.printf(":%06o %06o %s %s %s\t%s",
                                diff.getOldMode().getBits(),
                                diff.getNewMode().getBits(),
                                diff.getOldId().name(),
                                diff.getNewId().name(),
                                statusOf(diff),
                                diff.getChangeType() == DiffEntry.ChangeType.ADD ? diff.getNewPath() : diff.getOldPath());

                        if (hasNewPath(diff)) {
                            pw.printf(" %s", diff.getNewPath()); // copied to
                        }
                        pw.println();
                        pw.println();
                    }
                }
            }
        }
    }

}
