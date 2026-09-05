# Ramanujan Language: (Deprecated)

[← Back to main README](../README.md)

> **⚠️ Deprecated:** The `ramanujan` language described below is no longer under active development. All new development effort is now focused on the [Python support](python-support.md), so that computations can be written in plain Python instead of a custom language. This document is kept for historical/reference purposes only.

### Variables:
Variables in `ramanujan` are declared using the `var` keyword. Like in most programming languages, the type of the
variable has to be explicitly given. Currently, the supported types are `integer`, `double`.

Example:
```
var x:integer;
var y:double;
```

### Arrays:
Arrays in `ramanujan` are declared using the `var` keyword. The size of the array be both variable and explicitly given. This is a special kind of variable and the data type has to be given as `array`.

Example:
```
var arr[100]:array;
var n : double;
n =10;
var arr1[n]: array
```

### Functions:
Functions in `ramanujan` are declared using the `def` keyword. The functions do not have any return type. The parameters
of the functions are both input and output. The parameters have to be declared with the `var` keyword. The parameters
are passed by reference.
To invoke a function, the `exec` keyword is used.

Example:
```
def add(var x:integer, var y:integer, var ans:integer) {
    ans = x + y;
}
exec add(1, 2, ans);
```
Here, the value of `x` is `1`, the value of `y` is `2`, and the value of `ans` NULL when passed to the function. But, since
the arguments are passed by reference, the value of `ans` is `3` after the function is executed.

### Loops:
`ramanujan` supports `while` loops. The syntax is similar to most programming languages.

Example:
```
var i:integer;
i = 0;
while(i < 10) {
    i = i + 1;
}
```

### If-Else:
`ramanujan` supports `if-else` statements. The syntax is similar to most programming languages.

Example:
```
var x:integer;
x = 10;
if(x < 10) {
    x = 0;
} else {
    x = 1;
}
```


### Distributing computation over multiple devices:
There are two methods in Ramanujan which need to be used to distribute the computation over multiple devices:
1. `threadStart`:
   The `threadStart` keyword is used to define a new thread. Any new variable can be introduced in the thread, or it
   can use the variables defined outside the thread. in global scope The thread can call any function defined in the code.
   Syntax:
    ```
    threadStart(t0) {
        // code
    }
    ```
    Here, `t0` is the name of the thread. The thread name has to be unique in the code.

   Example:
   ```
   threadStart(t0) {
       exec add(1, 2, ans);
   }
   ```

2. `threadOnEnd`:
   The `threadOnEnd` keyword is used to define actions that should be taken when one or more threads complete.
   The body code executes **only on the last iteration**.
   Syntax:
    ```
    threadOnEnd(thread_seperated_thread_names, number_of_iterations) {
        // code
    }
    ```
   Example:
    ```
    threadOnEnd(t0, t1, 5) {
        // code
    }
    ```
    Here, `t0`, `t1` are the names of the threads. The last argument
    `5` is the number of iterations the threads have to run. The code defined in `threadOnEnd` for the n-1 times (here 4 times)
    would spawn the threads again. On the n-th time, the code defined in `threadOnEnd` would be executed.
   
            T1     T1   T1     T1     T1
         /    \  /  \  / \     / \     / \
       X       Y    Y      Y      Y       Z
         \    /  \   / \  /    \ /    \  /
            T0     T0   T0     T0     T0
   Here, In `Y` nodes, its just joining the thread and would do nothing, but on the last iteration, it would execute the code
    defined in `threadOnEnd` as node `Z`.

3. `threadParallelismCycle`:
   The `threadParallelismCycle` keyword is used to define actions that should be taken **after every cycle** of parallelism.
   Unlike `threadOnEnd`, which only executes its body on the final iteration, `threadParallelismCycle` executes its body
   after **each and every** completed cycle (including the last one).
   Syntax:
    ```
    threadParallelismCycle(thread_names, number_of_iterations) {
        // code – runs after every cycle
    }
    ```
   Example:
    ```
    threadParallelismCycle(t0, t1, 5) {
        // this block runs after each of the 5 cycles
    }
    ```

   The DAG structure for `threadParallelismCycle(t0, t1, 3) { body }`:
   ```
       T0      T0      T0
     /    \  /    \  /    \
   X    body   body   body   (final)
     \    /  \    /  \    /
       T1      T1      T1
   ```
   Each `body` node runs after every pair of T0+T1 threads completes. After each body (except the last), the threads
   are re-spawned for the next cycle.

   > **Important**: Do **not** combine `threadParallelismCycle` and `threadOnEnd` on the **same** thread set.
   > `threadParallelismCycle` already runs its body after the final cycle too, so `threadOnEnd` is
   > redundant. When added on the same threads, `threadOnEnd` injects an extra empty join node as a
   > second successor of every first-cycle thread (instead of the expected single cycle-body successor),
   > and overwrites the internal re-spawn map entries that `threadParallelismCycle` set up. This breaks
   > the cycle chain and creates an unintended parallel execution path.
   >
   > If you want a dedicated block that only runs after the **last** cycle, use `threadOnEnd` alone
   > (without `threadParallelismCycle`). If you want code that runs after **every** cycle (including the
   > last), use `threadParallelismCycle` alone.

Example of complex threading:
```
      T2       T3
      / \    /   \
    N0   N1      N2
      \ /    \   /
      T1       T4
 ```
Following code would help here:
```
threadStart(t1) {
}
threadStart(t2) {
}
threadOnEnd(t1, t2, 1) {
   threadStart(t3) {
   }
    threadStart(t4) {
    }
}
threadOnEnd(t3, t4, 1) {
}
```

Example of a distributed gradient descent with Particle Swarm Optimization in `ramanujan` language:
```
def getSquared(var xPow:integer, var yPow:integer, var ans:integer) {
    if(xPow < yPow) {
        ans = yPow - xPow;
    } else {
        ans = xPow - yPow;
    }
}
def getAvg(var arr:array, var originalArr:array, var avgF:integer) {
  var index,ans1,tmpAvg1,tmpAvg2:integer;
    avgF = 0;
    index = 0;
    while(index < 100) {
        tmpAvg1 = arr[index];
        tmpAvg2 = originalArr[index];
        exec getSquared(tmpAvg1,tmpAvg2, ans1);
        avgF = avgF + ans1;
        index = index + 1;
    }
    avgF = avgF / 100;
}

def getTestArr(var xTest:integer, var yTest:integer, var testArrTest:array) {
    var it:integer;
    it = 0;
    while(it < 100) {
        testArrTest[it] = xTest * it + yTest;
        it = it + 1;
    }
}

var train[100]:array;
var i:integer;
i = 0;
while(i < 100) {
    train[i] = i * 1.9 + 33;
    i = i + 1;
}

def mainCode(var train : array, var x1:double, var y1:double) {
    var x1,y1,j,avg,diff1,diff2x,diff2y,tmp:double;
    j = 0;
    var testArr[100]:array;
    var slope:double;
    var nexty,nextx:double;
    testArr[1] = 1;
    while(j < 15000) {
        exec getTestArr(x1,y1,testArr);
        exec getAvg(testArr, train, diff1);

        tmp = x1 + 0.0001;
        exec getTestArr(tmp,y1,testArr);
        exec getAvg(testArr, train, diff2x);

        slope = (diff2x - diff1) / 0.0001;
        nextx = x1 - slope * 0.1;

        tmp = y1 + 0.0001;
        exec getTestArr(x1,tmp,testArr);
        exec getAvg(testArr, train, diff2y);

        slope = (diff2y - diff1) / 0.0001;
        nexty = y1 - slope * 0.50;

        x1 = nextx;
        y1 = nexty;

        j = j + 1;
    }
}

var x1[100][10],y1[100][10]:array;
x1[0][0] = 0;
y1[0][0] = 0;
var ansX1,ansy1 :double;
ansX1 = 0;
ansy1 = 0;
var iteration[10]:array;
i = 0;
while(i < 10) {
    iteration[i] = 0;
    i = i + 1;
}


def getBest(var train:array, var best:integer, var x1:array, var y1:array, var iteration:integer) {
    best = 0;
    var index:integer;
    var bestM:double;
    bestM = 1000000000;
    index = 0;
    while(index < 10) {
        var testArr[100]:array;
        testArr[0] = 0;
        var testX1,testY1:double;
        testX1 = x1[index][iteration];
        testY1 = y1[index][iteration];
        exec getTestArr(testX1,testY1,testArr);
        var avg:double;
        avg = 0;
        exec getAvg(testArr, train, avg);
        if(avg < bestM) {
            bestM = avg;
            best = index;
        }
        index = index + 1;
    }
  }


  def posRun(var thread:integer, var train:array, var x1:array, var y1:array, var iteration :array) {
    var currentIter:integer;
    currentIter = iteration[thread];
    if(currentIter == 0) {
  
      x1[thread][currentIter]=thread;
      y1[thread][currentIter]=thread;
    } else {
      var best :integer;
      best=0;
      var thisIter:integer;
      thisIter=currentIter;
      currentIter = currentIter-1;
      exec getBest(train, best, x1, y1, currentIter);
      if(x1[thread][currentIter] < x1[best][currentIter]) {
          x1[thread][thisIter] = x1[thread][currentIter]+(x1[best][currentIter]-x1[thread][currentIter])/2;
        } else {
          x1[thread][thisIter] = x1[thread][currentIter]-(x1[thread][currentIter]-x1[best][currentIter])/2;
      }
      if(y1[thread][currentIter] < y1[best][currentIter]) {
          y1[thread][thisIter] = y1[thread][currentIter]+(y1[best][currentIter]-y1[thread][currentIter])/2;
        } else {
          y1[thread][thisIter] = y1[thread][currentIter]-(y1[thread][currentIter]-y1[best][currentIter])/2;
      }
      currentIter=thisIter;
    }
  

    var x,y:double;
    x=x1[thread][currentIter];
    y=y1[thread][currentIter];
    exec mainCode(train, x, y);
    x1[thread][currentIter]=x;
    y1[thread][currentIter]=y;
  }

  threadStart(t0) {
    exec posRun(0, train, x1, y1,iteration);
    iteration[0]=iteration[0]+1;
  }
  
  threadStart(t1) {
    exec posRun(1, train, x1, y1,iteration);
    iteration[1]=iteration[1]+1;
  
  }
  threadStart(t2) {
    exec posRun(2, train, x1, y1,iteration);
    iteration[2]=iteration[2]+1;
  
  }
  threadStart(t3) {
    exec posRun(3, train, x1, y1,iteration);
    iteration[3]=iteration[3]+1;
  }
  threadStart(t4) {
    exec posRun(4, train, x1, y1,iteration);
    iteration[4]=iteration[4]+1;
  }
  threadStart(t5) {
    exec posRun(5, train, x1, y1,iteration);
    iteration[5]=iteration[5]+1;
  
  }
  threadStart(t6) {
    exec posRun(6, train, x1, y1,iteration);
    iteration[6]=iteration[6]+1;
  
  }
  threadStart(t7) {
    exec posRun(7, train, x1, y1,iteration);
    iteration[7]=iteration[7]+1;
  
  }
  threadStart(t8) {
    exec posRun(8, train, x1, y1,iteration);
    iteration[8]=iteration[8]+1;
  
  }
  threadStart(t9) {
    exec posRun(9, train, x1, y1,iteration);
    iteration[9]=iteration[9]+1;
  
  }
  
  threadOnEnd(t0,t1,t2,t3,t4,t5,t6,t7,t8,t9,5) {
    var best:integer;
    best=0;
    exec getBest(train, best, x1, y1, 4);
    ansX1=x1[best][4];
    ansy1=y1[best][4];
  }
```


## How fast is it from Python?:
The `ramanujan` language is faster than the Python3 language. The above single node code runs in ~350 ms. The same heuristic
in Python3 takes ~410 ms. The device it was tested on was a MacBook Air M3 : 8GB RAM, Apple M3 chip.


