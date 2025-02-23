package org.commonjava.build.grid.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.commonjava.build.grid.format.BuildOrder;
import org.commonjava.build.grid.format.ProjectRef;
import org.commonjava.build.grid.format.DependencyRef;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Mojo( name="buildorder", aggregator = true, defaultPhase = LifecyclePhase.INITIALIZE, requiresProject = true )
public class BuildOrderMojo implements org.apache.maven.plugin.Mojo
{
    @Parameter( defaultValue = "${session}" ,readonly = true )
    private MavenSession session;

    @Parameter( defaultValue = ".grid/buildorder.yaml", property = "buildorder.output", readonly = true )
    private File output;

    private Log log;

    public void execute()
    {
        ProjectDependencyGraph graph = session.getProjectDependencyGraph();
        List<MavenProject> sortedProjects = graph.getSortedProjects();

        BiConsumer<MavenProject, ProjectRef> toProjectRef = ( dependencyProject, projectRef ) -> projectRef.addDependencyRef(
                        new DependencyRef( Path.of( session.getExecutionRootDirectory() ), dependencyProject.getFile(), dependencyProject.getGroupId(),
                                           dependencyProject.getArtifactId() ) );

        Function<MavenProject, ProjectRef> mapper = p -> {
            ProjectRef pd =
                            new ProjectRef( Path.of( session.getExecutionRootDirectory() ), p.getFile(),
                                            p.getGroupId(), p.getArtifactId() );

            graph.getUpstreamProjects( p, false ).forEach( dependencyProject -> toProjectRef.accept( dependencyProject, pd ) );

            if ( sortedProjects.contains( p.getParent() ) )
            {
                MavenProject parent = p.getParent();
                toProjectRef.accept( parent, pd );
            }

            return pd;
        };

        List<ProjectRef> projectDependencies =
                        sortedProjects.stream().map( mapper ).collect( Collectors.toList() );

        try
        {
            ObjectMapper om = new ObjectMapper( YAMLFactory.builder()
                                                             .enable( YAMLGenerator.Feature.MINIMIZE_QUOTES )
                                                             .build() );

            if ( output.getParentFile() != null )
            {
                output.getParentFile().mkdirs();
            }

            om.writeValue( output, new BuildOrder( projectDependencies ) );
            log.info( "Wrote build order to: " + output );
        }
        catch ( IOException e )
        {
            log.error( "Failed to write YAML build order to " + output, e );
        }
    }

    @Override
    public void setLog( Log log )
    {
        this.log = log;
    }

    @Override
    public Log getLog()
    {
        return log;
    }
}
