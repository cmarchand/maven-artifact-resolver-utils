/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package top.marchand.maven.artifact.resolver.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.ArtifactCoordinate;
import org.apache.maven.shared.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.artifact.resolve.ArtifactResult;
import org.apache.maven.shared.dependencies.DefaultDependableCoordinate;
import org.apache.maven.shared.dependencies.DependableCoordinate;
import org.codehaus.plexus.util.StringUtils;

/**
 *
 * @author cmarchand
 */
public class ArtifactResolverUtils {

    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile( "(.+)::(.*)::(.+)" );
    
    private final Map<String, ArtifactRepositoryLayout> repositoryLayouts;
    private final List<ArtifactRepository> repoList;
    private final List<ArtifactRepository> pomRemoteRepositories;
    private final MavenSession session;
    private final ArtifactResolver artifactResolver;
    private final ArtifactHandlerManager artifactHandlerManager;
    
    /**
     * Constructs a new ArtifactResolverUtils
     * @param session. May be declared with a {@code &#064;Parameter} annotation
     * @param artifactResolver. May be declared with a {@code &#064;Component} annotation
     * @param artifactHandlerManager. May be declared with a {@code &#064;Component} annotation
     * @param repositoryLayouts. May be declared with a {@code &#064;Component(role = ArtifactRepositoryLayout.class)} annotation
     * @param pomRemoteRepositories . May be declared with a {@code &#064;Parameter(defaultValue="${project.remoteArtifactRepositories}", readonly=true, required=true)} annotation
     */
    public ArtifactResolverUtils(
            final MavenSession session, 
            final ArtifactResolver artifactResolver,
            final ArtifactHandlerManager artifactHandlerManager,
            Map<String, ArtifactRepositoryLayout> repositoryLayouts, 
            List<ArtifactRepository> pomRemoteRepositories) {
        super();
        this.session=session;
        this.artifactResolver=artifactResolver;
        this.artifactHandlerManager=artifactHandlerManager;
        this.repositoryLayouts=repositoryLayouts;
        repoList = new ArrayList<>();
        this.pomRemoteRepositories=pomRemoteRepositories;
    }
    
    /**
     * Constructs the complete repo list where to serach in.
     * This method <strong>must</strong> be call before any other methods.
     * @param remoteRepositories The remote repository comma-separated list. May be null.
     * @throws MojoFailureException In case of invalid remote repository syntax
     */
    public final void constructRepoList(String remoteRepositories) throws MojoFailureException {
        ArtifactRepositoryPolicy always =
            new ArtifactRepositoryPolicy( true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS,
                                          ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN );
        if(pomRemoteRepositories != null) {
            repoList.addAll(pomRemoteRepositories);
        }

        if (remoteRepositories != null) {
            // Use the same format as in the deploy plugin id::layout::url
            List<String> repos = Arrays.asList(StringUtils.split(remoteRepositories, ","));
            for(String repo : repos) {
                repoList.add( parseRepository(repo, always) );
            }
        }
    }
    
    /**
     * Resolves the coordinates to an Artifact.
     * @param coordinate The artifact to search for
     * @return The resolved Artifact
     * @throws ArtifactResolverException In case of not found
     */
    public Artifact resolveArtifact(final DefaultDependableCoordinate coordinate) throws ArtifactResolverException {
        ProjectBuildingRequest buildingRequest =
            new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );

        buildingRequest.setRemoteRepositories( repoList );

        ArtifactResult artifactResult = artifactResolver.resolveArtifact( buildingRequest, toArtifactCoordinate( coordinate ) );
        Artifact foundArtifact = artifactResult.getArtifact();
        return foundArtifact;
    }

    private ArtifactCoordinate toArtifactCoordinate( DependableCoordinate dependableCoordinate ) {
        ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler( dependableCoordinate.getType() );
        DefaultArtifactCoordinate artifactCoordinate = new DefaultArtifactCoordinate();
        artifactCoordinate.setGroupId( dependableCoordinate.getGroupId() );
        artifactCoordinate.setArtifactId( dependableCoordinate.getArtifactId() );
        artifactCoordinate.setVersion( dependableCoordinate.getVersion() );
        artifactCoordinate.setClassifier( dependableCoordinate.getClassifier() );
        artifactCoordinate.setExtension( artifactHandler.getExtension() );
        return artifactCoordinate;
    }
    
    /**
     * Parse a remote repository specification, and constructs the repository
     * @param repo The repo to search in
     * @param policy The policy to use
     * @return The constructed Repository
     * @throws MojoFailureException In case of invalid syntax
     */
    protected ArtifactRepository parseRepository( String repo, ArtifactRepositoryPolicy policy ) throws MojoFailureException {
        // if it's a simple url
        String id = "temp";
        ArtifactRepositoryLayout layout = getLayout( "default" );
        String url = repo;

        // if it's an extended repo URL of the form id::layout::url
        if ( repo.contains( "::" ) ) {
            Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher( repo );
            if ( !matcher.matches() ) {
                throw new MojoFailureException(
                        repo, 
                        "Invalid syntax for repository: " + repo,
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

    private ArtifactRepositoryLayout getLayout( String id ) throws MojoFailureException {
        ArtifactRepositoryLayout layout = repositoryLayouts.get( id );
        if ( layout == null ) {
            throw new MojoFailureException( id, "Invalid repository layout", "Invalid repository layout: " + id );
        }
        return layout;
    }
    
}
