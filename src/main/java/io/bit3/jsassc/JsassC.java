package io.bit3.jsassc;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.bit3.jsass.CompilationException;
import io.bit3.jsass.Compiler;
import io.bit3.jsass.Options;
import io.bit3.jsass.Output;
import io.bit3.jsass.OutputStyle;

/**
 * Compiler implementation of jsass.
 */
public class JsassC {
  /**
   * Callable used with the executor.
   */
  static class CompilerRunnerCallable implements Callable<Long> {
    /**
     * The compiler to run.
     */
    private final CompileRunner runner;

    public CompilerRunnerCallable(final CompileRunner runner) {
      this.runner = runner;
    }

    @Override
    public Long call() throws Exception {
      runner.run();
      return runner.getExecutionTimeMillis();
    }
  }

  /**
   * A compiler runnable.
   */
  interface CompileRunner extends Runnable {
    /**
     * Return the compilation output.
     */
    Output getOutput();

    /**
     * Return the compilation execution time.
     */
    long getExecutionTimeMillis();
  }

  /**
   * Chain compiler that is used for benchmarking.
   *
   * The chain will be executed until a compilation failed. The execution time of all chained
   * compilers get summarized.
   */
  static class ChainCompileRunner implements CompileRunner {
    /**
     * The compiler chain.
     */
    private final Collection<CompileRunner> runners;

    /**
     * The last compilation output.
     */
    private Output output;

    /**
     * The summarized compilation time.
     */
    private long executionTimeMillis = -1;

    ChainCompileRunner(Collection<CompileRunner> runners) {
      this.runners = runners;
    }

    @Override
    public void run() {
      executionTimeMillis = 0;

      for (CompileRunner runner : runners) {
        runner.run();

        executionTimeMillis += runner.getExecutionTimeMillis();
        output = runner.getOutput();


        if (0 == output.getErrorStatus()) {
          System.out.println(
              String.format(
                  "Compiler %d finished after %d milliseconds",
                  runner.hashCode(),
                  runner.getExecutionTimeMillis()
              )
          );
        } else {
          System.err.println(
              String.format(
                  "Compiler %d failed after %d milliseconds",
                  runner.hashCode(),
                  runner.getExecutionTimeMillis()
              )
          );
          System.err.println(output.getErrorMessage());
          System.err.println(output.getErrorJson());
        }
      }
    }

    @Override
    public Output getOutput() {
      return output;
    }

    @Override
    public long getExecutionTimeMillis() {
      return executionTimeMillis;
    }
  }

  /**
   * Abstract time tracking compiler base.
   */
  static abstract class AbstractCompileRunner implements CompileRunner {
    /**
     * The jsass compiler.
     */
    protected final Compiler compiler;

    /**
     * The jsass compiler options.
     */
    protected final Options options;

    /**
     * The compilation output.
     */
    private Output output;

    /**
     * The compilation execution time.
     */
    private long executionTimeMillis = -1;

    public AbstractCompileRunner(boolean compress, URI sourceMap) throws URISyntaxException {
      compiler = new Compiler();
      options = new Options();
      options.setOutputStyle(compress ? OutputStyle.COMPRESSED : OutputStyle.NESTED);
      options.setSourceMapFile(null == sourceMap ? null : sourceMap);
    }

    @Override
    public final void run() {
      long timeMillis = System.currentTimeMillis();
      try {
        output = compile();
      } catch (CompilationException e) {
        e.printStackTrace();
      }
      executionTimeMillis = System.currentTimeMillis() - timeMillis;
    }

    /**
     * Execute the compilation.
     */
    protected abstract Output compile() throws CompilationException;

    @Override
    public Output getOutput() {
      return output;
    }

    @Override
    public long getExecutionTimeMillis() {
      return executionTimeMillis;
    }
  }

  /**
   * Time tracking file compiler.
   */
  static class CompileFileRunner extends AbstractCompileRunner {
    /**
     * The input file.
     */
    private final URI in;

    /**
     * The output file.
     */
    private final URI out;

    public CompileFileRunner(boolean compress, URI in, URI out, URI sourceMap) throws URISyntaxException {
      super(compress, sourceMap);
      this.in = in;
      this.out = out;
    }

    @Override
    protected Output compile() throws CompilationException {
      return compiler.compileFile(in, out, options);
    }
  }

  /**
   * Time tracking string compiler.
   */
  static class CompileStringRunner extends AbstractCompileRunner {
    /**
     * The source string.
     */
    private final String source;

    /**
     * The input path.
     */
    private final URI in;

    /**
     * The output path.
     */
    private final URI out;

    public CompileStringRunner(boolean compress, String source, URI sourceMap) throws URISyntaxException {
      super(compress, sourceMap);
      this.source = source;
      in = new URI("source.scss");
      out = new URI("source.css");
    }

    @Override
    protected Output compile() throws CompilationException {
      return compiler.compileString(source, in, out, options);
    }
  }

  /**
   * Execute the application.
   */
  public static void main(String[] args) {
    final org.apache.commons.cli.Options options = new org.apache.commons.cli.Options();
    options.addOption("h", "help", false, "Show this help.");
    options.addOption("b", "bench", true, "Run benchmark with X iterations.");
    options.addOption("t", "threads", true, "Run benchmark with X threads.");
    options.addOption("c", "compress", false, "Compress (minify) the output.");
    options.addOption("m", "source-map", false, "Generate source map.");

    try {
      final CommandLineParser parser = new DefaultParser();
      final CommandLine commandLine = parser.parse(options, args);

      if (commandLine.hasOption('h')) {
        showHelp(options);
        return;
      }

      List<String> argList = commandLine.getArgList();

      URI in = null;
      URI out = null;
      URI map = null;

      if (argList.size() > 2) {
        throw new ParseException("To many arguments");
      } else if (2 == argList.size()) {
        in = new File(argList.get(0)).toURI();
        out = new File(argList.get(1)).toURI();
      } else if (1 == argList.size()) {
        in = new File(argList.get(0)).toURI();
        out = new File(argList.get(0).replaceAll("\\.s[ac]ss$", "") + ".css").toURI();
      }

      if (commandLine.hasOption('m')) {
        map = null == out ? new File("style.css.map").toURI() : new URI(out.toString() + ".map");
      }

      boolean compress = commandLine.hasOption('c');

      if (commandLine.hasOption('b')) {
        int iterations = Integer.parseInt(commandLine.getOptionValue('b'));
        int threads = commandLine.hasOption('t') ? Integer.parseInt(commandLine.getOptionValue('t')) : 1;
        runBench(iterations, threads, in, out, map, compress);
      } else {
        runOnce(in, out, map, compress);
      }
    } catch (ParseException e) {
      System.err.println(e.getMessage());
      showHelp(options);
    } catch (URISyntaxException | IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Run benchmark compilation.
   */
  private static void runBench(int iterations, int threads, URI in, URI out, URI map, boolean compress) throws IOException, URISyntaxException {
    ExecutorService executor = Executors.newFixedThreadPool(threads);

    int iterationsPerThread = iterations / threads;
    iterations = iterationsPerThread * threads;

    Collection<Callable<Long>> tasks = new LinkedList<>();
    for (int i = 0; i < threads; i++) {
      Collection<CompileRunner> runners = new LinkedList<>();

      for (int j = 0; j < iterationsPerThread; j++) {
        CompileRunner runner = createCompileRunner(in, out, map, compress);
        runners.add(runner);
      }

      CompileRunner runner = new ChainCompileRunner(runners);
      CompilerRunnerCallable callable = new CompilerRunnerCallable(runner);
      tasks.add(callable);
    }

    try {
      long executionTime = executor
          .invokeAll(tasks)
          .stream()
          .mapToLong(JsassC::getCatch)
          .sum();
      long seconds = executionTime / 1000;
      long minutes = seconds / 60;
      seconds %= 60;

      System.out.println("Finished benchmark");
      System.out.println(
          String.format(
              "  total %d compilations",
              iterations
          )
      );
      System.out.println(
          String.format(
              "  with %d threads",
              threads
          )
      );
      System.out.println(
          String.format(
              "  total %d milliseconds / %d:%02d minutes",
              executionTime,
              minutes,
              seconds
          )
      );
      System.out.println(
          String.format(
              "  per compilation %d milliseconds",
              executionTime / iterations
          )
      );
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      executor.shutdown();
    }
  }

  /**
   * Run compilation and write output to file.
   */
  private static void runOnce(URI in, URI out, URI map, boolean compress) throws IOException, URISyntaxException {
    CompileRunner runner = createCompileRunner(in, out, map, compress);

    runner.run();

    Output output = runner.getOutput();

    if (0 != output.getErrorStatus()) {
      System.err.println(String.format("Compilation failed after %d milliseconds", runner.getExecutionTimeMillis()));
      System.err.println(output.getErrorMessage());
      System.err.println(output.getErrorJson());
      System.exit(1);
    } else if (null == in || null == out) {
      System.err.println(String.format("Compilation finished after %d milliseconds", runner.getExecutionTimeMillis()));
    } else {
      System.out.println(String.format("Compilation finished after %d milliseconds", runner.getExecutionTimeMillis()));
    }

    if (null == out) {
      System.out.println(output.getCss());
    } else {
      try (
          OutputStream os = new FileOutputStream(out.getPath());
      ) {
        IOUtils.write(output.getCss(), os);
      }
    }

    if (null != map) {
      try (
          OutputStream os = new FileOutputStream(map.getPath());
      ) {
        IOUtils.write(output.getSourceMap(), os);
      }
    }
  }

  /**
   * Exception catching Future::get wrapper.
   */
  private static long getCatch(Future<Long> future) {
    try {
      return future.get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Create a new compiler runnable.
   */
  private static CompileRunner createCompileRunner(URI in, URI out, URI map, boolean compress) throws IOException, URISyntaxException {
    CompileRunner runner;

    if (null == in || null == out) {
      String source = IOUtils.toString(System.in);
      runner = new CompileStringRunner(compress, source, map);
    } else {
      runner = new CompileFileRunner(compress, in, out, map);
    }
    return runner;
  }

  /**
   * Show the command line help.
   */
  private static void showHelp(final org.apache.commons.cli.Options options) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("jsassc [<in> [<out>]]", options);
  }
}
