package hudson.plugins.git;

import hudson.model.Run;
import hudson.scm.RepositoryBrowser;
import org.kohsuke.stapler.export.Exported;

import java.util.List;

/**
 * ChangeSetList with parse all branches change log
 *
 * @author ltf
 * @since 2019/6/26, 下午4:01
 */
public class AllGitChangeSetList extends GitChangeSetList {
    private final GitChangeSetList allBranchesChangeSet;

    AllGitChangeSetList(Run build, RepositoryBrowser<?> browser, List<GitChangeSet> logs, GitChangeSetList allBranchesChangeSet) {
        super(build, browser, logs);
        this.allBranchesChangeSet = allBranchesChangeSet;
    }

    @Exported
    public String getKind() {
        return "git4branches";
    }

    @Exported
    public GitChangeSetList getAllBranchesChangeSet(){
        return allBranchesChangeSet;
    }
}
