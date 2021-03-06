package com.github.sgov.server.service;

import com.github.sgov.server.exception.NotFoundException;
import com.github.sgov.server.exception.PublicationException;
import com.github.sgov.server.model.VocabularyContext;
import com.github.sgov.server.model.Workspace;
import com.github.sgov.server.service.repository.GithubRepositoryService;
import com.github.sgov.server.service.repository.VocabularyService;
import com.github.sgov.server.service.repository.WorkspaceRepositoryService;
import com.github.sgov.server.util.VocabularyFolder;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.topbraid.shacl.validation.ValidationReport;

/**
 * Workspace-related business logic.
 */
@Service
@Slf4j
public class WorkspaceService {

    private final WorkspaceRepositoryService repositoryService;

    private final VocabularyService vocabularyService;

    private final GithubRepositoryService githubService;

    /**
     * Constructor.
     */
    @Autowired
    public WorkspaceService(WorkspaceRepositoryService repositoryService,
                            VocabularyService vocabularyService,
                            GithubRepositoryService githubService) {
        this.repositoryService = repositoryService;
        this.vocabularyService = vocabularyService;
        this.githubService = githubService;
    }


    public List<String> getAllWorkspaceIris() {
        return repositoryService.getAllWorkspaceIris();
    }

    /**
     * Validates the workspace with the given IRI.
     *
     * @param workspaceUri Workspace that should be created.
     */
    public ValidationReport validate(URI workspaceUri) {
        final Workspace workspace = getWorkspace(workspaceUri);
        return repositoryService.validateWorkspace(workspace);
    }

    private Workspace getWorkspace(URI workspaceUri) {
        final Workspace workspace = repositoryService.findRequired(workspaceUri);
        if (workspace == null) {
            throw new NotFoundException("Vocabulary context " + workspaceUri + " does not exist.");
        }
        return workspace;
    }

    private String createPullRequestBody(final Workspace workspace) {
        return MessageFormat.format("Changed vocabularies: \n - {0}", workspace
            .getVocabularyContexts()
            .stream()
            .map(c -> c.getBasedOnVocabularyVersion().toString() + " (kontext " + c.getUri() + ")")
            .collect(Collectors.joining("\n - "))
        );
    }

    private String createBranchName(final String workspaceUriString) {
        return
            "PL-publish-" + workspaceUriString
                .substring(workspaceUriString.lastIndexOf("/") + 1);
    }

    /**
     * Validates the workspace with the given IRI.
     *
     * @param workspaceUri Workspace that should be created.
     * @return GitHub PR URL
     */
    public URI publish(URI workspaceUri) {
        final Workspace workspace = getWorkspace(workspaceUri);

        final String workspaceUriString = workspaceUri.toString();
        final String branchName = createBranchName(workspaceUriString);

        final File dir = Files.createTempDir();

        try (final Git git = githubService.checkout(branchName, dir)) {
            for (final VocabularyContext c : workspace.getVocabularyContexts()) {
                final URI iri = c.getBasedOnVocabularyVersion();
                final VocabularyFolder f = VocabularyFolder.ofVocabularyIri(dir, iri);

                if (f == null) {
                    throw new PublicationException("Invalid vocabulary IRI " + iri);
                }
                vocabularyService.storeContext(c, f);
                githubService.commit(git, MessageFormat.format(
                    "Publishing vocabulary {0} in workspace {1}", iri, workspaceUriString));
            }

            githubService.push(git);

            FileUtils.deleteDirectory(dir);

            String prUrl = githubService.createOrUpdatePullRequestToMaster(branchName,
                MessageFormat.format("Publishing workspace {0}", workspaceUriString),
                createPullRequestBody(workspace));
            return URI.create(prUrl);
        } catch (IOException e) {
            throw new PublicationException("An exception occurred during publishing workspace.", e);
        }
    }

    public Workspace persist(Workspace instance) {
        repositoryService.persist(instance);
        return instance;
    }

    public Workspace findInferred(URI id) {
        return repositoryService.findInferred(id);
    }

    /**
     * Updates only direct attributes of the workspace.
     *
     * @param workspace Workspace that holds updated attributes.
     */
    public void update(Workspace workspace) {
        Workspace update = repositoryService.findRequired(workspace.getUri());
        update.setLabel(workspace.getLabel());
        repositoryService.update(update);
    }

    public void remove(URI id) {
        repositoryService.remove(id);
    }

    public Workspace getRequiredReference(URI id) {
        return repositoryService.getRequiredReference(id);
    }

    /**
     * Ensures that a vocabulary with the given IRI is registered in the workspace. If yes, its
     * content is kept intact. Otherwise a new context is created and the content is loaded from
     *
     * @param workspaceUri  URI of the workspace to connect the vocabulary context to.
     * @param vocabularyUri URI of the vocabulary to be attached to the workspace
     * @param isReadOnly    true if the context should be created as read-only (no effect if the
     *                      vocabulary already exists)
     * @return URI of the vocabulary context to create
     */
    public URI ensureVocabularyExistsInWorkspace(
        URI workspaceUri, URI vocabularyUri, boolean isReadOnly) {
        URI vocabularyContextUri =
            repositoryService.getVocabularyContextReference(workspaceUri, vocabularyUri);
        if (vocabularyContextUri == null) {
            VocabularyContext vocabularyContext =
                repositoryService.createVocabularyContext(workspaceUri, vocabularyUri, isReadOnly);
            vocabularyService.loadContext(vocabularyContext);
            vocabularyContextUri = vocabularyContext.getUri();
        }

        return vocabularyContextUri;
    }

    public List<Workspace> findAllInferred() {
        return repositoryService.findAllInferred();
    }

    /**
     * Removes vocabulary context from given workspace.
     *
     * @param workspaceId         Uri of a workspace.
     * @param vocabularyContextId Uri of a vocabulary context.
     */
    public VocabularyContext removeVocabulary(URI workspaceId, URI vocabularyContextId) {
        Workspace workspace = repositoryService.findRequired(workspaceId);
        VocabularyContext vocabularyContext = workspace.getVocabularyContexts().stream()
            .filter(vc -> vc.getUri().equals(vocabularyContextId))
            .findFirst().orElseThrow(
                () -> NotFoundException.create(
                    VocabularyContext.class.getSimpleName(), vocabularyContextId
                )
            );
        workspace.getVocabularyContexts().remove(vocabularyContext);
        repositoryService.update(workspace);
        return vocabularyContext;
    }
}
