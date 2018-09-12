# mavn-artifact-resolver-utils
Utilites to resolve artifacts in a maven plugin

Use is quite simple, but requires some code...

```
@Mojo(name="xxx")
public MyMojo extends AbstractMojo {

  @Parameter(defaultValue = "${session}", readonly = true)
  private MavenSession session;

  @Component
  private org.apache.maven.shared.artifact.resolve.ArtifactResolver artifactResolver;

  @Component
  private ArtifactHandlerManager artifactHandlerManager;

  @Component( role = ArtifactRepositoryLayout.class )
  private Map<String, ArtifactRepositoryLayout> repositoryLayouts;

  @Parameter( defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true )
  private List<ArtifactRepository> pomRemoteRepositories;

  /**
   * Repositories in the format id::[layout]::url or just url, separated by comma. ie.
   * central::default::http://repo1.maven.apache.org/maven2,myrepo::::http://repo.acme.com,http://repo.acme2.com
   */
  @Parameter( property = "remoteRepositories" )
  private String remoteRepositories;

  @Override
  public void execute() ... {
    // construct resolver
    ArtifactResolverUtils resolverUtils = new ArtifactResolverUtils(
        session, 
        artifactResolver, 
        artifactHandlerManager, 
        repositoryLayouts, 
        pomRemoteRepositories);
    resolverUtils.constructRepoList(remoteRepositories);

    // construct coordinates to look for
    DefaultDependableCoordinate coordinate = new DefaultDependableCoordinate();
    coordinate.setGroupId("groupId");
    coordinate.setArtifactId("artifactId");
    // if different classifier
    coordinate.setClassifier("classifier");
    coordinate.setVersion("1.0.0");
    // resolves artifact
    Artifact foundArtifact = resolverUtils.resolveArtifact(coordinate);

    // your stuff...
  }
}
```

This first method is perfect to resolve dependencies, i.e. jar artifacts.

If you need to resolve an artifact that is not a jar, you have to use a ArtifactCoordinate instead of a DependableCoordinate :

```
    ArtifactCoordinate coordinate = new DefaultArtifactCoordinate();
    coordinate.setGroupId("groupId");
    coordinate.setArtifactId("artifactId");
    coordiante.setExtension("zip");
    coordinate.setClassifier("delivery");
    coordinate.setVersion("1.0.0");
    Artifact foundArtifact = resolverUtils.resolveArtifact(coordinate);
```

Have fun !
