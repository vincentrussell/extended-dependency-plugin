package com.github.vincentrussell;

import me.alexpanov.net.FreePortFinder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Random;

public class GetSourcesMojoTest extends AbstractMojoTestCase {

    private int httpPort = FreePortFinder.findFreeLocalPort();
    private Server jettyServer;

    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder();

    File jettyNexusBaseDir;
    File localBaseDir;
    ArtifactRepository localRepo;
    File localReleaseArtifactDir;
    File remoteReleaseArtifactDir;
    File localSnapshotArtifactDir;
    File remoteSnapshotArtifactDir;

    @Override
    protected void setUp() throws Exception {
        temporaryFolder.create();
        jettyNexusBaseDir = temporaryFolder.newFolder("jetty-remote");
        localBaseDir = temporaryFolder.newFolder("local-base-dir");
        localRepo = createLocalArtifactRepository(localBaseDir);
        jettyServer = new Server();
        ServerConnector httpConnector = new ServerConnector(jettyServer);
        ServletHandler servletHandler = new ServletHandler();
        NexusServlet nexusServlet = new NexusServlet(jettyNexusBaseDir);
        ServletHolder servletHolder = new ServletHolder(nexusServlet);
        servletHandler.addServletWithMapping(servletHolder, "/repository/thirdparty/*");
        httpConnector.setPort(httpPort);
        jettyServer.setConnectors(new Connector[] {httpConnector});
        jettyServer.setHandler(servletHandler);
        jettyServer.start();
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        jettyServer.stop();
        temporaryFolder.delete();
        super.tearDown();
    }

    @Test
    public void testSourcesTransitiveFalseSources() throws Exception {
        String artifactId = "cool-artifact";
        String releaseVersion = "1.0";

        String config = "<remoteRepositories>http://localhost:" + httpPort + "/repository/thirdparty/</remoteRepositories>\n" +
                "                    <artifact>com.github.vincentrussell:"+artifactId+":"+releaseVersion+":jar:sources</artifact>\n" +
                "                    <transitive>false</transitive>";

        MavenProject mavenProject = readMavenProject(new TestProjectConfig(temporaryFolder).getFile(config).getParentFile());
        mavenProject.getProperties().put("maven.repo.local", localBaseDir.getAbsolutePath());
        MavenSession session = finishSessionCreation(newMavenSession( mavenProject ));
        simulateRemoteMavenFiles(jettyNexusBaseDir, artifactId, releaseVersion, "");

        MojoExecution execution = newMojoExecution( "get" );
        GetMojo getSourcesMojo = (GetMojo) lookupConfiguredMojo( session, execution );

        assertFalse(Paths.get(localBaseDir.getAbsolutePath(), "com", "github", "vincentrussell", "cool-artifact", "1.0", "cool-artifact-1.0-sources.jar").toFile().exists());
        getSourcesMojo.execute();
        assertTrue(Paths.get(localBaseDir.getAbsolutePath(), "com", "github", "vincentrussell", "cool-artifact", "1.0", "cool-artifact-1.0-sources.jar").toFile().exists());
    }

    @Test
    public void testSourcesTransitiveFalseJavadoc() throws Exception {

        String artifactId = "cool-artifact";
        String releaseVersion = "1.0";

        String config = "<remoteRepositories>http://localhost:" + httpPort + "/repository/thirdparty/</remoteRepositories>\n" +
                "                    <artifact>com.github.vincentrussell:"+artifactId+":"+releaseVersion+":jar:javadoc</artifact>\n" +
                "                    <transitive>false</transitive>";

        MavenProject mavenProject = readMavenProject(new TestProjectConfig(temporaryFolder).getFile(config).getParentFile());
        mavenProject.getProperties().put("maven.repo.local", localBaseDir.getAbsolutePath());
        MavenSession session = finishSessionCreation(newMavenSession( mavenProject ));
        simulateRemoteMavenFiles(jettyNexusBaseDir, artifactId, releaseVersion, "");

        MojoExecution execution = newMojoExecution( "get" );
        GetMojo getSourcesMojo = (GetMojo) lookupConfiguredMojo( session, execution );

        assertFalse(Paths.get(localBaseDir.getAbsolutePath(), "com", "github", "vincentrussell", "cool-artifact", "1.0", "cool-artifact-1.0-javadoc.jar").toFile().exists());
        getSourcesMojo.execute();
        assertTrue(Paths.get(localBaseDir.getAbsolutePath(), "com", "github", "vincentrussell", "cool-artifact", "1.0", "cool-artifact-1.0-javadoc.jar").toFile().exists());
    }

    @Test
    public void testSourcesTransitiveTrueSources() throws Exception {

        String artifactId = "cool-artifact";
        String releaseVersion = "1.0";

        String config = "<remoteRepositories>http://localhost:" + httpPort + "/repository/thirdparty/</remoteRepositories>\n" +
                "                    <artifact>com.github.vincentrussell:cool-artifact:1.0:jar:sources</artifact>\n" +
                "                    <transitive>true</transitive>";

        MavenProject mavenProject = readMavenProject(new TestProjectConfig(temporaryFolder).getFile(config).getParentFile());
        mavenProject.getProperties().put("maven.repo.local", localBaseDir.getAbsolutePath());
        MavenSession session = finishSessionCreation(newMavenSession( mavenProject ));
        simulateRemoteMavenFiles(jettyNexusBaseDir, "cool-artifact-3", "2.5", "");
        simulateRemoteMavenFiles(jettyNexusBaseDir, "cool-artifact-2", "2.4", "<dependency>\n" +
                "      <groupId>com.github.vincentrussell</groupId>\n" +
                "      <artifactId>cool-artifact-3</artifactId>\n" +
                "      <version>2.5</version>\n" +
                "    </dependency>\n");
        simulateRemoteMavenFiles(jettyNexusBaseDir, artifactId, releaseVersion, "<dependency>\n" +
                "      <groupId>com.github.vincentrussell</groupId>\n" +
                "      <artifactId>cool-artifact-2</artifactId>\n" +
                "      <version>2.4</version>\n" +
                "    </dependency>\n");

        MojoExecution execution = newMojoExecution( "get" );
        GetMojo getSourcesMojo = (GetMojo) lookupConfiguredMojo( session, execution );

        assertFalse(Paths.get(localBaseDir.getAbsolutePath(), "com", "github", "vincentrussell", "cool-artifact", "1.0", "cool-artifact-1.0-sources.jar").toFile().exists());
        assertFalse(Paths.get(localBaseDir.getAbsolutePath(), "com", "github", "vincentrussell", "cool-artifact-2", "2.4", "cool-artifact-2-2.4-sources.jar").toFile().exists());
        assertFalse(Paths.get(localBaseDir.getAbsolutePath(), "com", "github", "vincentrussell", "cool-artifact-3", "2.5", "cool-artifact-3-2.5-sources.jar").toFile().exists());
        getSourcesMojo.execute();
        assertTrue(Paths.get(localBaseDir.getAbsolutePath(), "com", "github", "vincentrussell", "cool-artifact", "1.0", "cool-artifact-1.0-sources.jar").toFile().exists());
        assertTrue(Paths.get(localBaseDir.getAbsolutePath(), "com", "github", "vincentrussell", "cool-artifact-2", "2.4", "cool-artifact-2-2.4-sources.jar").toFile().exists());
        assertTrue(Paths.get(localBaseDir.getAbsolutePath(), "com", "github", "vincentrussell", "cool-artifact-3", "2.5", "cool-artifact-3-2.5-sources.jar").toFile().exists());
    }

    @Test
    public void testSourcesTransitiveTrueJavadoc() throws Exception {

        String artifactId = "cool-artifact";
        String releaseVersion = "1.0";

        String config = "<remoteRepositories>http://localhost:" + httpPort + "/repository/thirdparty/</remoteRepositories>\n" +
                "                    <artifact>com.github.vincentrussell:cool-artifact:1.0:jar:javadoc</artifact>\n" +
                "                    <transitive>true</transitive>";

        MavenProject mavenProject = readMavenProject(new TestProjectConfig(temporaryFolder).getFile(config).getParentFile());
        mavenProject.getProperties().put("maven.repo.local", localBaseDir.getAbsolutePath());
        MavenSession session = finishSessionCreation(newMavenSession( mavenProject ));
        simulateRemoteMavenFiles(jettyNexusBaseDir, "cool-artifact-3", "2.5", "");
        simulateRemoteMavenFiles(jettyNexusBaseDir, "cool-artifact-2", "2.4", "<dependency>\n" +
                "      <groupId>com.github.vincentrussell</groupId>\n" +
                "      <artifactId>cool-artifact-3</artifactId>\n" +
                "      <version>2.5</version>\n" +
                "    </dependency>\n");
        simulateRemoteMavenFiles(jettyNexusBaseDir, artifactId, releaseVersion, "<dependency>\n" +
                "      <groupId>com.github.vincentrussell</groupId>\n" +
                "      <artifactId>cool-artifact-2</artifactId>\n" +
                "      <version>2.4</version>\n" +
                "    </dependency>\n");

        MojoExecution execution = newMojoExecution( "get" );
        GetMojo getSourcesMojo = (GetMojo) lookupConfiguredMojo( session, execution );

        assertFalse(Paths.get(localBaseDir.getAbsolutePath(), "com", "github", "vincentrussell", "cool-artifact", "1.0", "cool-artifact-1.0-javadoc.jar").toFile().exists());
        assertFalse(Paths.get(localBaseDir.getAbsolutePath(), "com", "github", "vincentrussell", "cool-artifact-2", "2.4", "cool-artifact-2-2.4-javadoc.jar").toFile().exists());
        assertFalse(Paths.get(localBaseDir.getAbsolutePath(), "com", "github", "vincentrussell", "cool-artifact-3", "2.5", "cool-artifact-3-2.5-javadoc.jar").toFile().exists());
        getSourcesMojo.execute();
        assertTrue(Paths.get(localBaseDir.getAbsolutePath(), "com", "github", "vincentrussell", "cool-artifact", "1.0", "cool-artifact-1.0-javadoc.jar").toFile().exists());
        assertTrue(Paths.get(localBaseDir.getAbsolutePath(), "com", "github", "vincentrussell", "cool-artifact-2", "2.4", "cool-artifact-2-2.4-javadoc.jar").toFile().exists());
        assertTrue(Paths.get(localBaseDir.getAbsolutePath(), "com", "github", "vincentrussell", "cool-artifact-3", "2.5", "cool-artifact-3-2.5-javadoc.jar").toFile().exists());
    }

    private static void verifyDirsAreEqual(final Path one, final Path other) throws IOException {
        Files.walkFileTree(one, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file,
                                             BasicFileAttributes attrs) throws IOException {
                FileVisitResult result = super.visitFile(file, attrs);

                // get the relative file name from path "one"
                Path relativize = one.relativize(file);
                // construct the path for the counterpart file in "other"
                Path fileInOther = other.resolve(relativize);
                
                assertEquals(file.toFile().getName(), fileInOther.toFile().getName());
                return result;
            }
        });
    }

    private MavenSession finishSessionCreation(MavenSession newMavenSession) throws NoLocalRepositoryManagerException {
        DefaultRepositorySystemSession defaultRepositorySystem = (DefaultRepositorySystemSession) newMavenSession.getRepositorySession();
        SimpleLocalRepositoryManagerFactory simpleLocalRepositoryManagerFactory = new SimpleLocalRepositoryManagerFactory();
        LocalRepositoryManager localRepositoryManager = simpleLocalRepositoryManagerFactory.newInstance(defaultRepositorySystem, new LocalRepository(localBaseDir));
        defaultRepositorySystem.setLocalRepositoryManager(localRepositoryManager);
        newMavenSession.getRequest().setLocalRepository(localRepo);
        return newMavenSession;
    }

    private void simulateRemoteMavenFiles(File remoteBaseDir, String artifactId, String version, String dependencies) throws IOException {
        File coolArtifactDir = getBaseDirectoryForArtifact(remoteBaseDir, artifactId, version);
        coolArtifactDir.mkdirs();
        createFile(artifactId, version, coolArtifactDir, ".jar");
        createFile(artifactId, version, coolArtifactDir, ".jar.sha1");
        createFile(artifactId, version, coolArtifactDir, "-javadoc.jar");
        createFile(artifactId, version, coolArtifactDir, "-javadoc.jar.sha1");
        File pomFile =createFile(artifactId, version, coolArtifactDir, ".pom");
        String pomString = IOUtils.toString(getClass().getResourceAsStream("/samplePom/pom.xml"), "UTF-8")
                    .replaceAll("\\$artifactId", artifactId)
                    .replaceAll("\\$version", version)
                    .replaceAll("\\$dependencies", !StringUtils.isEmpty(dependencies)
                            ? "   <dependencies>\n"
                            + dependencies + "\n"
                            + "    </dependencies>" : "");
        pomFile.delete();
        FileUtils.write(pomFile, pomString, "UTF-8");
        createFile(artifactId, version, coolArtifactDir, ".pom.sha1");
        createFile(artifactId, version, coolArtifactDir, "-sources.jar");
        createFile(artifactId, version, coolArtifactDir, "-sources.jar.sha1");
    }

    private File getBaseDirectoryForArtifact(File localBaseDir, String artifactId, String version) {
        return Paths.get(localBaseDir.getAbsolutePath(), "com", "github", "vincentrussell", artifactId, version).toFile();
    }

    private File createFile(String artifactId, String version, File coolArtifactDir, String extension) throws IOException {
        File file = new File(coolArtifactDir, artifactId + "-" + version + extension);
        FileUtils.writeByteArrayToFile(file, getRandomByteArray());
        return file;
    }

    private byte[] getRandomByteArray() {
        byte[] b = new byte[2000];
        new Random().nextBytes(b);
        return b;
    }

    private ArtifactRepository createLocalArtifactRepository(File localRepoDir) {
        return new MavenArtifactRepository("local",
                localRepoDir.toURI().toString(),
                new DefaultRepositoryLayout(),
                new ArtifactRepositoryPolicy(true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE),
                new ArtifactRepositoryPolicy(true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE)

        );
    }

    protected MavenProject readMavenProject(File basedir )
            throws ProjectBuildingException, Exception
    {
        File pom = new File( basedir, "pom.xml" );
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setBaseDirectory( basedir );
        ProjectBuildingRequest configuration = request.getProjectBuildingRequest();
        DefaultRepositorySystemSession repositorySession = new DefaultRepositorySystemSession();
        configuration.setRepositorySession(repositorySession);
        MavenProject project = lookup( ProjectBuilder.class ).build( pom, configuration ).getProject();
        assertNotNull( project );
        return project;
    }



    public static class NexusServlet extends HttpServlet {

        private final File baseDir;

        public NexusServlet(File baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

            String url = request.getRequestURI();

            if (url.endsWith("maven-metadata.xml")) {
                response.setContentType("application/xml");
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().println("" +
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<metadata>\n" +
                        "  <groupId>com.github.vincentrussell</groupId>\n" +
                        "  <artifactId>doesnt-matter</artifactId>\n" +
                        "  <versioning>\n" +
                        "    <release>0.1.1</release>\n" +
                        "    <versions>\n" +
                        "      <version>0.1.1</version>\n" +
                        "    </versions>\n" +
                        "    <lastUpdated>20200608005752</lastUpdated>\n" +
                        "  </versioning>\n" +
                        "</metadata>\n");
            } else {
                File file = Paths.get(baseDir.getAbsolutePath(), request.getPathInfo()).toFile();
               if (file.exists()) {
                   response.setContentType(getContentType(FilenameUtils.getExtension(file.getName())));
                   FileUtils.copyFile(file, response.getOutputStream());
               } else {
                   response.setStatus(HttpServletResponse.SC_NOT_FOUND);
               }
            }


        }

        private String getContentType(String extension) {
            switch (extension) {
                case "xml": return "text/xml";
                case "pom": return "text/xml";
                case "jar": return "application/java-archive";
                default: return "application/octet-stream";
            }
        }

        @Override
        protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

            String pathInfo = request.getPathInfo();

            File file = Paths.get(baseDir.getAbsolutePath(), request.getPathInfo()).toFile();
            file.getParentFile().mkdirs();

            try(FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                IOUtils.copy(request.getInputStream(), fileOutputStream);
            }

            response.setContentType("plain/text");
            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().println("OK");
        }
    }

}