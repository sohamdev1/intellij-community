package com.intellij.dependencies;

import com.intellij.cyclicDependencies.ShortestPathUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import junit.framework.TestCase;

import java.util.*;

/**
 * User: anna
 * Date: Feb 11, 2005
 */
public class DijkstraAlgorithmTest extends TestCase{
  private Graph<String> myGraph;
  public void test1() throws Exception{
    final HashMap<String, String []> graph = new HashMap<String, String[]>();
    graph.put("a", new String[]{"b","c", "d"});
    graph.put("b", new String[]{"d", "c"});
    graph.put("c", new String[]{"b"});
    graph.put("d", new String[0]);
    myGraph = new GraphGenerator<String>(new GraphGenerator.SemiGraph<String>() {
      public Collection<String> getNodes() {
        return graph.keySet();
      }

      public Iterator<String> getIn(final String n) {
        String[] in = graph.get(n);
        if (in == null){
          in = ArrayUtil.EMPTY_STRING_ARRAY;
        }
        return Arrays.asList(in).iterator();
      }
    });
    final ShortestPathUtil algorithm = new ShortestPathUtil(myGraph);
    final List shortestPath = algorithm.getShortestPath("a", "b");
    checkResult(new String[]{"b","a"}, shortestPath);
  }

  public void test2() throws Exception{
    final HashMap<String, String []> graph = new HashMap<String, String[]>();
    graph.put("a", new String[]{"b", "d"});
    graph.put("b", new String[]{"d"});
    graph.put("c", new String[]{"a"});
    graph.put("d", new String[]{"a", "c"});
    myGraph = new GraphGenerator<String>(new GraphGenerator.SemiGraph<String>() {
      public Collection<String> getNodes() {
        return graph.keySet();
      }

      public Iterator<String> getIn(final String n) {
        String[] in = graph.get(n);
        if (in == null){
          in = ArrayUtil.EMPTY_STRING_ARRAY;
        }
        return Arrays.asList(in).iterator();
      }
    });
    final ShortestPathUtil algorithm = new ShortestPathUtil(myGraph);
    final List shortestPath = algorithm.getShortestPath("b", "c");
    checkResult(new String[]{"c","d","b"}, shortestPath);
  }

  public void test3() throws Exception{
    final HashMap<String, String []> graph = new HashMap<String, String[]>();
    graph.put("a", new String[]{"c", "d"});
    graph.put("b", new String[]{"a"});
    graph.put("c", new String[]{"d"});
    graph.put("d", new String[]{"a", "b"});
    myGraph = new GraphGenerator<String>(new GraphGenerator.SemiGraph<String>() {
      public Collection<String> getNodes() {
        return graph.keySet();
      }

      public Iterator<String> getIn(final String n) {
        String[] in = graph.get(n);
        if (in == null){
          in = ArrayUtil.EMPTY_STRING_ARRAY;
        }
        return Arrays.asList(in).iterator();
      }
    });
    final ShortestPathUtil algorithm = new ShortestPathUtil(myGraph);
    final List shortestPath = algorithm.getShortestPath("c", "b");
    checkResult(new String[]{"b","d","c"}, shortestPath);
  }

  public void test4() throws Exception{
      final HashMap<String, String []> graph = new HashMap<String, String[]>();
    graph.put("a", new String[]{"c"});
    graph.put("b", new String[]{"a"});
    graph.put("c", new String[]{"e"});
    graph.put("d", new String[]{"b"});
    graph.put("e", new String[]{"d", "f", "a"});
    graph.put("f", new String[]{"d"});
    myGraph = new GraphGenerator<String>(new GraphGenerator.SemiGraph<String>() {
      public Collection<String> getNodes() {
        return graph.keySet();
      }

      public Iterator<String> getIn(final String n) {
        String[] in = graph.get(n);
        if (in == null){
          in = ArrayUtil.EMPTY_STRING_ARRAY;
        }
        return Arrays.asList(in).iterator();
      }
    });
    final ShortestPathUtil algorithm = new ShortestPathUtil(myGraph);
    final List shortestPath = algorithm.getShortestPath("c", "b");
    checkResult(new String[]{"b","d","e","c"}, shortestPath);
  }

  private void checkResult(final String [] expectedPath, List<String> path){
    assertNotNull(path);
    assertEquals(expectedPath.length, path.size());
    int index = 0;
    for (Iterator<String> iterator = path.iterator(); iterator.hasNext();) {
      String s = iterator.next();
      assertEquals(expectedPath[index++], s);
    }
  }
}
