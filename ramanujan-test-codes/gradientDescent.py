# getSquared: Calculates absolute difference of two numbers
def getSquared(xPow, yPow):
    ans = 0
    if xPow < yPow:
        ans = yPow - xPow
    else:
        ans = xPow - yPow
    return ans

# getAvg: Calculates average squared difference between two arrays
def getAvg(arr, originalArr):
    avgF = 0
    index = 0
    ans1 = 0
    tmpAvg1 = 0
    tmpAvg2 = 0
    while index < 100:
        tmpAvg1 = arr[index]
        tmpAvg2 = originalArr[index]
        ans1 = getSquared(tmpAvg1, tmpAvg2)
        avgF = avgF + ans1
        index = index + 1
    avgF = avgF / 100
    return avgF

# getTestArr: Fills array with linear values based on coefficients
def getTestArr(xTest, yTest, testArrTest):
    it = 0
    while it < 100:
        testArrTest[it] = xTest * it + yTest
        it = it + 1

# Initialize training data array
train = [0 for _ in range(100)]
i = 0
while i < 100:
    train[i] = i * 1.9 + 33
    i = i + 1

# mainCode: Gradient descent optimization, modifies x1 and y1 in place
def mainCode(train, x1, y1):
    j = 0
    testArr = [0 for _ in range(100)]
    slope = 0
    nexty = 0
    nextx = 0
    tmp = 0
    diff1 = 0
    diff2x = 0
    diff2y = 0
    testArr[1] = 1
    while j < 15000:
        getTestArr(x1, y1, testArr)
        diff1 = getAvg(testArr, train)

        tmp = x1 + 0.0001
        getTestArr(tmp, y1, testArr)
        diff2x = getAvg(testArr, train)

        slope = (diff2x - diff1) / 0.0001
        nextx = x1 - slope * 0.1

        tmp = y1 + 0.0001
        getTestArr(x1, tmp, testArr)
        diff2y = getAvg(testArr, train)

        slope = (diff2y - diff1) / 0.0001
        nexty = y1 - slope * 0.50

        x1 = nextx
        y1 = nexty

        j = j + 1

# Initialize x1 and y1 coefficient arrays (2D arrays for thread management)
x1 = [[0 for _ in range(10)] for _ in range(100)]
y1 = [[0 for _ in range(10)] for _ in range(100)]
x1[0][0] = 0
y1[0][0] = 0
ansX1 = 0
ansy1 = 0
iteration = [0 for _ in range(10)]
i = 0
while i < 10:
    iteration[i] = 0
    i = i + 1

# getBest: Find thread with lowest error, returns best index
def getBest(train, x1, y1, iteration):
    best = 0
    bestM = 1000000000
    index = 0
    testArr = [0 for _ in range(100)]
    testArr[0] = 0
    testX1 = 0
    testY1 = 0
    avg = 0
    while index < 10:
        testX1 = x1[index][iteration]
        testY1 = y1[index][iteration]
        getTestArr(testX1, testY1, testArr)
        avg = getAvg(testArr, train)
        if avg < bestM:
            bestM = avg
            best = index
        index = index + 1
    return best

# posRun: Run gradient descent for a specific thread
def posRun(thread, train, x1, y1, iteration):
    currentIter = 0
    currentIter = iteration[thread]
    best = 0
    thisIter = 0
    x = 0
    y = 0
    if currentIter == 0:
        x1[thread][currentIter] = thread
        y1[thread][currentIter] = thread
    else:
        best = 0
        thisIter = currentIter
        currentIter = currentIter - 1
        best = getBest(train, x1, y1, currentIter)
        if x1[thread][currentIter] < x1[best][currentIter]:
            x1[thread][thisIter] = x1[thread][currentIter] + (x1[best][currentIter] - x1[thread][currentIter]) / 2
        else:
            x1[thread][thisIter] = x1[thread][currentIter] - (x1[thread][currentIter] - x1[best][currentIter]) / 2
        if y1[thread][currentIter] < y1[best][currentIter]:
            y1[thread][thisIter] = y1[thread][currentIter] + (y1[best][currentIter] - y1[thread][currentIter]) / 2
        else:
            y1[thread][thisIter] = y1[thread][currentIter] - (y1[thread][currentIter] - y1[best][currentIter]) / 2
        currentIter = thisIter
    x = x1[thread][currentIter]
    y = y1[thread][currentIter]
    mainCode(train, x, y)
    x1[thread][currentIter] = x
    y1[thread][currentIter] = y

posRun(0, train, x1, y1, iteration)
ansX1 = x1[0][0]
ansy1 = y1[0][0]