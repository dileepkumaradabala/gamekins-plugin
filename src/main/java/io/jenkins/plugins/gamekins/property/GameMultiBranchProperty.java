package io.jenkins.plugins.gamekins.property;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.gamekins.statistics.Statistics;
import io.jenkins.plugins.gamekins.util.PropertyUtil;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.kohsuke.stapler.*;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;

public class GameMultiBranchProperty extends AbstractFolderProperty<AbstractFolder<?>> implements GameProperty {

    private boolean activated;
    private boolean showStatistics;
    private final ArrayList<String> teams;
    private final Statistics statistics;

    @DataBoundConstructor
    public GameMultiBranchProperty(AbstractItem job, boolean activated, boolean showStatistics) {
        this.activated = activated;
        this.showStatistics = showStatistics;
        this.teams = new ArrayList<>();
        this.statistics = new Statistics(job);
    }

    public boolean getActivated() {
        return this.activated;
    }

    @DataBoundSetter
    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public boolean getShowStatistics() {
        return this.showStatistics;
    }

    @DataBoundSetter
    public void setShowStatistics(boolean showStatistics) {
        this.showStatistics = showStatistics;
    }

    public ArrayList<String> getTeams() {
        return this.teams;
    }

    @Override
    public Statistics getStatistics() {
        return this.statistics;
    }

    public boolean addTeam(String teamName) throws IOException {
        if (this.teams.contains(teamName)) {
            return false;
        } else {
            this.teams.add(teamName);
            owner.save();
            return true;
        }
    }

    public boolean removeTeam(String teamName) throws IOException {
        if (!this.teams.contains(teamName)) {
            return false;
        } else {
            this.teams.remove(teamName);
            owner.save();
            return true;
        }
    }

    @Override
    public AbstractFolderProperty<?> reconfigure(StaplerRequest req, JSONObject form) {
        if (form != null) this.activated = form.getBoolean("activated");
        if (form != null) this.showStatistics = form.getBoolean("showStatistics");
        PropertyUtil.reconfigure(owner, this.activated, this.showStatistics);
        return this;
    }

    @Extension
    public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {

        public DescriptorImpl() {
            super();
            load();
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Set the activation of the Gamekins plugin.";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractFolder> containerType) {
            return containerType == WorkflowMultiBranchProject.class || super.isApplicable(containerType);
        }

        @Override
        public AbstractFolderProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new GameMultiBranchProperty((AbstractItem) req.findAncestor(AbstractItem.class).getObject(),
                    formData.getBoolean("activated"), formData.getBoolean("showStatistics"));
        }

        public FormValidation doAddTeam(@AncestorInPath WorkflowMultiBranchProject job,
                                        @QueryParameter String teamName) {
            if (teamName.isEmpty()) return FormValidation.error("Insert a name for the team");
            GameMultiBranchProperty property = (GameMultiBranchProperty) job.getProperties().get(this);
            FormValidation validation = PropertyUtil.doAddTeam( property, teamName);
            save();
            return validation;
        }

        public ListBoxModel doFillTeamsBoxItems(@AncestorInPath WorkflowMultiBranchProject job) {
            GameMultiBranchProperty property = job == null ? null
                    : (GameMultiBranchProperty) job.getProperties().get(this);
            return PropertyUtil.doFillTeamsBoxItems(property);
        }

        public ListBoxModel doFillUsersBoxItems() {
            return PropertyUtil.doFillUsersBoxItems();
        }

        public FormValidation doAddUserToTeam(@AncestorInPath WorkflowMultiBranchProject job,
                                              @QueryParameter String teamsBox, @QueryParameter String usersBox) {
            return PropertyUtil.doAddUserToTeam(job, teamsBox, usersBox);
        }

        public FormValidation doRemoveUserFromTeam(@AncestorInPath WorkflowMultiBranchProject job,
                                                   @QueryParameter String teamsBox, @QueryParameter String usersBox) {
            return PropertyUtil.doRemoveUserFromTeam(job, teamsBox, usersBox);
        }

        public FormValidation doDeleteTeam(@AncestorInPath WorkflowMultiBranchProject job,
                                           @QueryParameter String teamsBox) {
            String projectName = job.getName();
            GameMultiBranchProperty property = (GameMultiBranchProperty) job.getProperties().get(this);
            FormValidation validation = PropertyUtil.doDeleteTeam(projectName, property, teamsBox);
            save();
            return validation;
        }
    }
}