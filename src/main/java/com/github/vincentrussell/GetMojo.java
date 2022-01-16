package com.github.vincentrussell;


import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.transfer.artifact.ArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.apache.maven.shared.transfer.dependencies.DefaultDependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.DependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolverException;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Goal for bulk import into remote repository
 */
@Mojo( name = "get",
        requiresProject = false, threadSafe = true )
public class GetMojo extends AbstractMojo {

    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile( "(.+)::(.*)::(.+)" );

     /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    /**
     * Alternative location to upload artifacts from.  This directory must be in
     * the same format as an maven2 local repository.
     */
    @Parameter( property = "repositoryBase", required = false )
    private File repositoryBase;

    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    private MavenSession session;

    /**
     * The repository system.
     */
    @Component
    private RepositorySystem repositorySystem;

    @Component
    private ArtifactResolver artifactResolver;

    @Component
    private DependencyResolver dependencyResolver;

    /**
     * Used for attaching the artifacts to deploy to the project.
     */
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * Used for creating the project to which the artifacts to deploy will be attached.
     */
    @Component
    private ProjectBuilder projectBuilder;

    /**
     * Download transitively, retrieving the specified artifact and all of its dependencies.
     */
    @Parameter( property = "transitive", defaultValue = "true" )
    private boolean transitive = true;

    private DefaultDependableCoordinate coordinate = new DefaultDependableCoordinate();

    /**
     * A string of the form groupId:artifactId:version[:packaging[:classifier]].
     */
    @Parameter( property = "artifact" )
    private String artifact;

    @Parameter( defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true )
    private List<ArtifactRepository> pomRemoteRepositories;

    /**
     * Map that contains the layouts.
     */
    @Component( role = ArtifactRepositoryLayout.class )
    private Map<String, ArtifactRepositoryLayout> repositoryLayouts;

    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    /**
     * Repositories in the format id::[layout]::url or just url, separated by comma. ie.
     * central::default::https://repo.maven.apache.org/maven2,myrepo::::https://repo.acme.com,https://repo.acme2.com
     */
    @Parameter( property = "remoteRepositories" )
    private String remoteRepositories;

    /**
     * The groupId of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter( property = "groupId" )
    private String groupId;

    /**
     * The artifactId of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter( property = "artifactId" )
    private String artifactId;

    /**
     * The version of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter( property = "version" )
    private String version;

    /**
     * The classifier of the artifact to download. Ignored if {@link #artifact} is used.
     *
     * @since 2.3
     */
    @Parameter( property = "classifier" )
    private String classifier;

    /**
     * The packaging of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter( property = "packaging", defaultValue = "jar" )
    private String packaging = "jar";

    /**
     * Location of the file.
     */
    @Parameter( defaultValue = "${project.build.directory}", property = "outputDir", required = true )
    private File outputDirectory;

    public void execute() throws MojoExecutionException {

        if ( coordinate.getArtifactId() == null && artifact == null ) {
            throw new MojoExecutionException( "You must specify an artifact, "
                    + "e.g. -Dartifact=org.apache.maven.plugins:maven-downloader-plugin:1.0" );
        }
        if ( artifact != null ) {
            String[] tokens = StringUtils.split( artifact, ":" );
            if ( tokens.length < 3 || tokens.length > 5 ) {
                throw new MojoExecutionException( "Invalid artifact, you must specify "
                        + "groupId:artifactId:version[:packaging[:classifier]] " + artifact );
            }
            coordinate.setGroupId( tokens[0] );
            coordinate.setArtifactId( tokens[1] );
            coordinate.setVersion( tokens[2] );
            if ( tokens.length >= 4 ) {
                coordinate.setType( tokens[3] );
            }
            if ( tokens.length == 5 ) {
                coordinate.setClassifier( tokens[4] );
            }
        } else {
            coordinate.setGroupId( groupId );
            coordinate.setArtifactId( artifactId );
            coordinate.setVersion( version );
            if ( packaging != null ) {
                coordinate.setType( packaging );
            }
            if ( classifier != null ) {
                coordinate.setClassifier( classifier );
            }
        }

        ArtifactRepositoryPolicy always =
                new ArtifactRepositoryPolicy( true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS,
                        ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN );

        List<ArtifactRepository> repoList = new ArrayList<>();

        if ( pomRemoteRepositories != null ) {
            repoList.addAll( pomRemoteRepositories );
        }

        if ( remoteRepositories != null ) {
            // Use the same format as in the deploy plugin id::layout::url
            String[] repos = StringUtils.split( remoteRepositories, "," );
            for ( String repo : repos ) {
                repoList.add( parseRepository( repo, always ) );
            }
        }

        try {
            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );

            Settings settings = session.getSettings();
            repositorySystem.injectMirror( repoList, settings.getMirrors() );
            repositorySystem.injectProxy( repoList, settings.getProxies() );
            repositorySystem.injectAuthentication( repoList, settings.getServers() );

            buildingRequest.setRemoteRepositories( repoList );

            if ( transitive ) {
                getLog().info( "Resolving " + coordinate + " with transitive dependencies" );
                Iterable<ArtifactResult> results = dependencyResolver.resolveDependencies( buildingRequest, coordinate, null );
                for (Iterator<ArtifactResult> iterator = results.iterator(); iterator.hasNext();) {
                    if (iterator.hasNext()) {
                        ArtifactResult artifactResult = iterator.next();
                        try {
                            ArtifactCoordinate coordinate = toArtifactCoordinate(artifactResult, this.coordinate);
                            getLog().info( "Resolving " + coordinate );
                            artifactResolver.resolveArtifact(buildingRequest, coordinate);
                        } catch (Exception e) {
                            getLog().warn(e.getMessage(), e);
                        }
                    }
                }
            } else {
                getLog().info( "Resolving " + coordinate );
                artifactResolver.resolveArtifact( buildingRequest, toArtifactCoordinate( coordinate ) );
            }
        } catch ( ArtifactResolverException | DependencyResolverException e ) {
            throw new MojoExecutionException( "Couldn't download artifact: " + e.getMessage(), e );
        }

    }

    private ArtifactCoordinate toArtifactCoordinate(final ArtifactResult artifactResult, final DependableCoordinate dependableCoordinate) {
        Artifact artifact = artifactResult.getArtifact();
        ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler( artifact.getType() );
        DefaultArtifactCoordinate artifactCoordinate = new DefaultArtifactCoordinate();
        artifactCoordinate.setGroupId( artifact.getGroupId() );
        artifactCoordinate.setArtifactId( artifact.getArtifactId() );
        artifactCoordinate.setVersion( artifact.getVersion() );
        artifactCoordinate.setClassifier( dependableCoordinate.getClassifier() );
        artifactCoordinate.setExtension( artifactHandler.getExtension() );
        return artifactCoordinate;
    }


    ArtifactRepository parseRepository(final  String repo, final ArtifactRepositoryPolicy policy ) throws MojoExecutionException {
        // if it's a simple url
        String id = "temp";
        ArtifactRepositoryLayout layout = getLayout( "default" );
        String url = repo;

        // if it's an extended repo URL of the form id::layout::url
        if ( repo.contains( "::" ) ) {
            Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher( repo );
            if ( !matcher.matches() ) {
                throw new MojoExecutionException( repo, "Invalid syntax for repository: " + repo,
                        "Invalid syntax for repository. Use \"id::layout::url\" or \"URL\"." );
            }

            id = matcher.group( 1 ).trim();
            if ( !StringUtils.isEmpty( matcher.group( 2 ) ) ) {
                layout = getLayout( matcher.group( 2 ).trim() );
            }
            url = matcher.group( 3 ).trim();
        }
        return new MavenArtifactRepository( id, url, layout, policy, policy );
    }

    private ArtifactRepositoryLayout getLayout(final String id ) throws MojoExecutionException {
        ArtifactRepositoryLayout layout = repositoryLayouts.get( id );

        if ( layout == null ) {
            throw new MojoExecutionException( id, "Invalid repository layout", "Invalid repository layout: " + id );
        }

        return layout;
    }

    private ArtifactCoordinate toArtifactCoordinate(DependableCoordinate dependableCoordinate ) {
        ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler( dependableCoordinate.getType() );
        DefaultArtifactCoordinate artifactCoordinate = new DefaultArtifactCoordinate();
        artifactCoordinate.setGroupId( dependableCoordinate.getGroupId() );
        artifactCoordinate.setArtifactId( dependableCoordinate.getArtifactId() );
        artifactCoordinate.setVersion( dependableCoordinate.getVersion() );
        artifactCoordinate.setClassifier( dependableCoordinate.getClassifier() );
        artifactCoordinate.setExtension( artifactHandler.getExtension() );
        return artifactCoordinate;
    }
}
