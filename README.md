Jsass compiler application
==========================

This is a sass compiler application based on [jasss][jsass].
This project is designed for testing and benchmarking jsass, not for production usage!

Compile example
---------------

```bash
$ mvn clean package
$ bower install foundation
$ java -jar target/jsassc-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
       bower_components/foundation/scss/foundation.scss \
       foundation.css
Compilation finished after 643 milliseconds
```

Benchmark example
-----------------

*Hint: Benchmarking will never generate any css output!*

```bash
$ mvn clean package
$ bower install foundation
$ java -jar target/jsassc-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
       -b 12 -t 4 bower_components/foundation/scss/foundation.scss
Compiler 1932786264 finished after 1094 milliseconds
Compiler 1849229896 finished after 1120 milliseconds
Compiler 1533946501 finished after 1188 milliseconds
Compiler 600305775 finished after 1195 milliseconds
Compiler 1728287339 finished after 886 milliseconds
Compiler 91222812 finished after 1105 milliseconds
Compiler 1681019398 finished after 1048 milliseconds
Compiler 743025087 finished after 1161 milliseconds
Compiler 2061880749 finished after 902 milliseconds
Compiler 908592110 finished after 1071 milliseconds
Compiler 397341159 finished after 1142 milliseconds
Compiler 95892237 finished after 1094 milliseconds
Finished benchmark
  total 12 compilations
  with 4 threads
  total 13006 milliseconds / 0:13 minutes
  per compilation 1083 milliseconds
```

License
-------

MIT-License

[jsass]: https://github.com/bit3/jsass
