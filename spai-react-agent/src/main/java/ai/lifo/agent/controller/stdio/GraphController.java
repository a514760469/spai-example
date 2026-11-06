package ai.lifo.agent.controller.stdio;


import com.alibaba.cloud.ai.agent.studio.dto.GraphResponse;
import com.alibaba.cloud.ai.agent.studio.loader.GraphLoader;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * REST Controller for Graph-related API endpoints. Always registered.
 * Returns empty list when no graphs are available.
 *
 * @author zhanglifeng
 * @since 2026-11-05
 */
@Slf4j
@RestController("myGraphController")
public class GraphController {

    private final GraphLoader graphLoader;

    @Autowired
    public GraphController(GraphLoader graphLoader) {
        this.graphLoader = graphLoader;
        List<String> graphNames = this.graphLoader.listGraphs();
        log.info("GraphController initialized with 【{}】 graphs: 【{}】", graphNames.size(), graphNames);
    }

    /**
     * Lists available graphs.
     *
     * @return A list of graph names.
     */
    @GetMapping("/list-graphs")
    public List<String> listGraphs() {
        List<String> graphNames = graphLoader.listGraphs();
        log.info("Listing graphs. Found: {}", graphNames);
        return graphNames.stream().sorted().collect(toList());
    }

    /**
     * Returns the graph representation (Mermaid format) for the given graph name.
     *
     * @param graphName The name of the graph.
     * @return GraphResponse containing the Mermaid diagram source.
     */
    @GetMapping("/graphs/{graphName}/representation")
    public GraphResponse getGraphRepresentation(@PathVariable String graphName) {
        if (graphName == null || graphName.isBlank()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "graphName cannot be null or empty");
        }
        try {
            CompiledGraph graph = graphLoader.loadGraph(graphName);
            String title = graph.stateGraph != null ? graph.stateGraph.getName() : graphName;
            GraphRepresentation repr = graph.getGraph(GraphRepresentation.Type.MERMAID, title);
            return new GraphResponse(repr.content(), true);
        }
        catch (Exception e) {
            log.warn("Failed to get graph representation for: {}", graphName, e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Graph not found: " + graphName, e);
        }
    }
}