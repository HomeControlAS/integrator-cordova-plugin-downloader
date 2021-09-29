var exec = require('cordova/exec');

exports.download = function (arg0, success, error) {
    exec(success, error, 'Downloader', 'download', [arg0]);
};

exports.androidTargetSDK = function (arg0, success, error) {
    exec(success, error, 'Downloader', 'androidTargetSDK', [arg0]);
};
