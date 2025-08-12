// 性能测试脚本，测试 QuickJS-Android 在大量计算和循环中的表现

(function() {
    console.time("大循环性能测试");
    let sum = 0;
    for (let i = 0; i < 1e7; i++) {
        sum += i;
    }
    console.timeEnd("大循环性能测试");
    console.log("sum =", sum);

    console.time("数组操作性能测试");
    let arr = new Array(1e6);
    for (let i = 0; i < arr.length; i++) {
        arr[i] = i;
    }
    let filtered = arr.filter(x => x % 2 === 0);
    let mapped = filtered.map(x => x * 2);
    let reduced = mapped.reduce((acc, val) => acc + val, 0);
    console.timeEnd("数组操作性能测试");
    console.log("数组操作结果 =", reduced);

    console.time("递归性能测试");
    function factorial(n) {
        return n <= 1 ? 1 : n * factorial(n - 1);
    }
    let factResult = factorial(15);
    console.timeEnd("递归性能测试");
    console.log("factorial(15) =", factResult);

    console.time("Promise 异步性能测试");
    function asyncTask(i) {
        return new Promise(resolve => {
            setTimeout(() => resolve(i * 2), 1);
        });
    }
    let promises = [];
    for (let i = 0; i < 100; i++) {
        promises.push(asyncTask(i));
    }
    Promise.all(promises).then(results => {
        console.timeEnd("Promise 异步性能测试");
        console.log("Promise 结果示例:", results.slice(0, 5));
    });
})();
