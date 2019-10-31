[![](https://jitpack.io/v/me.aldebrn/ebisu-java.svg)](https://jitpack.io/#me.aldebrn/ebisu-java)

# Ebisu Java

This is a **Java** port of the original Python implementation of [Ebisu](https://github.com/fasiha/ebisu), a public-domain library intended for use by quiz apps to intelligently handle scheduling. See [Ebisu’s literate documentation](https://github.com/fasiha/ebisu) for *all* the details—this document is a quick guide to how to use Ebisu from Java and JVM languages.

## Install

See [JitPack](https://jitpack.io/#me.aldebrn/ebisu-java/v1.0) for Gradle, sbt, and lein instructions but for Maven, add JitPack as a repository:
```xml
	<repositories>
		<repository>
		    <id>jitpack.io</id>
		    <url>https://jitpack.io</url>
		</repository>
	</repositories>
```
and then the following dependency:
```xml
	<dependency>
	    <groupId>me.aldebrn</groupId>
	    <artifactId>ebisu-java</artifactId>
	    <version>Tag</version>
	</dependency>
```

## API

If you have Java9+ installed, clone this repo and follow along with jshell:
```
$ git clone https://github.com/fasiha/ebisu-java.git
$ cd ebisu-java
$ mvn com.github.johnpoth:jshell-maven-plugin:1.1:run -q
|  Welcome to JShell -- Version 11.0.1
|  For an introduction type: /help intro

jshell> import me.aldebrn.ebisu.*
```
The Maven call to `jshell-maven-plugin` will launch jshell with the correct classpath.

Then make sure you run `import me.aldebrn.ebisu.*` for the rest of this tutorial.

### Create a memory model for each flashcard: `new EbisuModel(...)`
The Ebisu algorithm uses three numbers to model each flashcard, and they’re called `α`, `β`, and `t`, that is, `alpha`, `beta`, and `time`.

> Statistics professor note: the algorithm treats recall probability at time `t` to be a [Beta](https://en.wikipedia.org/wiki/Beta_distribution)-distributed random variable with parameters `α` and `β`. See the document accompanying the [Ebisu Python](https://fasiha.github.io/ebisu/) implementation for all the mathematical derivations.

There are three constructors for the `EbisuModel` class, which allow you to set one, two, or all three parameters but, caveat!, their orders are a little different:
```java
double halflife = 0.25;
EbisuInterface model1 = new EbisuModel(halflife); // α=4, β=4, t=0.25

double alpha = 2;
EbisuInterface model2 = new EbisuModel(halflife, alpha); // α=2, β=α, t=0.25

double beta = 2;
EbisuInterface model3 = new EbisuModel(alpha, beta, halflife); // same as above
```
In jshell:
```java
jshell> double halflife = 0.25;
halflife ==> 0.25

jshell> EbisuInterface model1 = new EbisuModel(halflife); // α=4, β=4, t=0.25
model1 ==> Model(4.0, 4.0, 0.25)

jshell> double alpha = 2;
alpha ==> 2.0

jshell> EbisuInterface model2 = new EbisuModel(halflife, alpha); // α=4, β=4, t=0.25
model2 ==> Model(2.0, 2.0, 0.25)

jshell> double beta = 2;
beta ==> 2.0

jshell> EbisuInterface model3 = new EbisuModel(alpha, beta, halflife); // same as above
model3 ==> Model(2.0, 2.0, 0.25)
```

For a brand new flashcard, you want 
1. `alpha = beta` and
2. `alpha >= 2`.

The first requirement guarantees that the memory’s halflife is the halflife set above, 0.25. The *units* of times in Ebisu are up to you. I like using *hours*, in which case these models posit a memory that decays to 50% probability of recall in a quarter-hour.

The second requirement ensures that the probability distribution is valid. The higher the value of `alpha = beta`, the more confident you are that your memory for this flashcard has the halflife you gave it—in reality, you will have a lot of uncertainty about what the *true* halflife of each memory is, so lower is better. The lower this is, the more aggressively the algorithm updates its memory model based on flashcard quiz results. The current implementation picks α=β=4 for a reasonable update reponse.

After creating this memory model, store it, along with a timestamp, in your database. As we’ll see below, Ebisu only deals with elapsed time (in units you decide), and not timestamps, so you have to store the timestamp separately.

### Predict a model's current recall probability: `double predictRecall(EbisuInterface prior, double tnow[, boolean exact])`
You can ask Ebisu for the recall probability (or log-probability by default) for a memory model since the last time you calculated it:
```java
double timeElapsed = 0.25; // you figure this out based on a timestamp
double logRecallProbability = Ebisu.predictRecall(model1, timeElapsed);

double recallProbability = Ebisu.predictRecall(model1, timeElapsed, true);
```
In jshell:
```java
jshell> double timeElapsed = 0.25; // you figure this out based on a timestamp
timeElapsed ==> 0.25

jshell> double logRecallProbability = Ebisu.predictRecall(model1, timeElapsed);
logRecallProbability ==> -0.6931471805599458

jshell> double recallProbability = Ebisu.predictRecall(model1, timeElapsed, true);
recallProbability ==> 0.4999999999999997
```

Here we pretend that *exactly* the halflife has elapsed and we want to know what the recall probability is of a memory model we created above. By default, `predictRecall` will return a log-probability because this is a bit more computationally-efficient than the true probability. Log-probabilities can be sorted just like real probabilities (the lower the log-probability of recall, the lower the probability of recall), but sometimes you want the probability itself. Instead of calling `Math.exp` on the log-probability, you can pass a third parameter, `exact`, which, if true, will do the `Math.exp` for you.

Obviously, if `timeElapsed` is very small (i.e., one minute in the above example), the (log-)probability of recall will be very high. If the time elapsed is very large (a year), the log-probability of recall will be very low.

When you have learned many flashcards, each one with its own memory model object (and don't forget the timestamp!), you can loop through all of them, calling `predictRecall` on each, and finding the flashcard most in danger of being forgotten, and quizzing on that. After the student succeeds or fails the quiz, it is time to update the memory model.

### Update a recall probability model given a quiz result: `EbisuInterface updateRecall(EbisuInterface prior, boolean result, double tnow)`
Ebisu treats quiz results as boolean: either pass or fail. In either case, compute the time elapsed since this flashcard’s memory model was last created and then:
```java
boolean result = true;
double timeElapsed = 0.3;
EbisuInterface updatedModel = Ebisu.updateRecall(model1, result, timeElapsed);
```
In jshell:
```java
jshell> boolean result = true;
result ==> true

jshell> double timeElapsed = 0.3;
timeElapsed ==> 0.3

jshell> EbisuInterface updatedModel = Ebisu.updateRecall(model1, result, timeElapsed);
updatedModel ==> Model(5.2, 4.0, 0.25)
```
The updated model will have new `alpha`, `beta`, and `time` parameters inside it based on whether the quiz was success or not, and how much time has elapsed since the memory model was updated. Update your database with this new memory model and a timestamp of when the quiz occurred to continue the cycle.

> Statistics professor note: the Beta distribution at `time` on memory recall is assumed to decay exponentially, so is nonlinearly and exactly transformed into a [generalized Beta of the first kind (GB1)](https://en.wikipedia.org/w/index.php?title=Generalized_beta_distribution&oldid=913561520#Generalized_beta_of_first_kind_(GB1)). The quiz itself is modeled as a Bernoulli trial, with probability of success governed by this GB1 distribution. The posterior is non-conjugate but turns out to have analytically-tractable moments, which are transformed into a new Beta random variable, at some new `time` in the future close to the halflife, via moment-matching. Again, mathematical details accompany the [Ebisu Python](https://fasiha.github.io/ebisu/) reference implementation.

### Bonus: compute the time for a memory model’s probability of recall to decay to some percentile: `double modelToPercentileDecay(EbisuInterface model[, double percentile[, double tolerance]])`
For display purposes, or for caching purposes, it can be useful to know at what point in the future a given memory model will decay to a given percentile. This function answers that question.
```java
double halflife = Ebisu.modelToPercentileDecay(updatedModel); // 50% recall

double quintile = Ebisu.modelToPercentileDecay(updatedModel, 0.2); // 20% recall

double needlesslyAccurate = Ebisu.modelToPercentileDecay(updatedModel, 0.2, 1e-8);
```
In jshell:
```java
jshell> double halflife = Ebisu.modelToPercentileDecay(updatedModel); // 50% recall
halflife ==> 0.308666402072347

jshell> double quintile = Ebisu.modelToPercentileDecay(updatedModel, 0.2); // 20% recall
quintile ==> 0.8100819021650226

jshell> double needlesslyAccurate = Ebisu.modelToPercentileDecay(updatedModel, 0.2, 1e-8);
needlesslyAccurate ==> 0.8100994545777259
```
The second argument, `percentile`, has to be between 0 and 1 (exclusive), and 0.5 corresponds to halflife. The third argument, `tolerance`, specifies the tolerance around `percentile` that [Apache Commons' Golden section search](http://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/analysis/solvers/BisectionSolver.html) tries to meet: the default is 1e-4. So if you use the output of this `modelToPercentileDecay` as the input to `predictRecall`, like so:
```java
double diff = Ebisu.predictRecall(updatedModel, Ebisu.modelToPercentileDecay(updatedModel, 0.2, 1e-3), true) - 0.2;
```
then the difference `diff` would be ±1e-3. In jshell:
```
jshell> double diff = Ebisu.predictRecall(updatedModel, Ebisu.modelToPercentileDecay(updatedModel, 0.2, 1e-3), true) - 0.2;
diff ==> -5.4733037021220676E-5
```

## Dev

Run tests with `mvn test`.

## Acknowledgements

This is my first Java project. Comments and suggestions, either open a [GitHub issue](https://github.com/fasiha/ebisu-java/issues) or [contact me](https://fasiha.github.io/#contact).

Thanks to contributor [**@dbof10**](https://github.com/fasiha/ebisu-java/issues/1) who prodded me to update this Java port to feature parity with the JavaScript and Python reference implementations and pointed me to JitPack as the Javadistribution channel for the new millenium.
