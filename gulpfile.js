var gulp = require('gulp');
var RevAll = require('gulp-rev-all');
var argv = require('yargs').argv;
var gzip = require('gulp-gzip');
var jsonTransform = require('gulp-json-transform');
var merge = require('merge-stream');
var gulpIgnore = require('gulp-ignore');
var debug = require('gulp-debug');
var runSequence = require('run-sequence');
var shell = require('gulp-shell');
var del = require('del');
var postcss = require("gulp-postcss");
var uglify = require('gulp-uglify');
var fs = require('fs');
var path = require('path');
var exec = require('child_process').exec;

gulp.task('css', function () {
  return gulp.src(['./resources/css/*.css'])
    .pipe(postcss([
      require('postcss-import')(),
      require('postcss-custom-media')(),
      require('postcss-custom-properties')(),
      require('postcss-calc')(),
      require('postcss-color-function')(),
      require('postcss-discard-comments')(),
      require('autoprefixer')({browsers: ['last 3 versions']}),
      /* require('postcss-reporter')(), */
      /* comment out cssnano to see uncompressed css */
      require('cssnano')()
    ]))
    .pipe(gulp.dest('./resources/public/css'));
});

gulp.task('watch', ['css'], function (cb) {
  gulp.watch('./resources/css/*.css', ['css']);
});

gulp.task('default', ['css']);

gulp.task('refresh-deps', function () {
  /* Run this after you update node module versions. */
  /* Maybe there's a preferred way of including node modules in cljs projects? */
  return gulp.src(['./node_modules/react-slick/dist/react-slick.js'])
      .pipe(gulp.dest('src-cljs/storefront/'));
});

gulp.task('clean-min-js', function () {
  return del(['./target/min-js']);
});

gulp.task('minify-js', ['clean-min-js'], function () {
  return gulp.src('src-cljs/storefront/*.js')
    .pipe(uglify())
    .pipe(gulp.dest('target/min-js/'));
});

gulp.task('cljs-build', shell.task(['lein cljsbuild once release']));

gulp.task('copy-release-assets', function () {
  return gulp.src(['./target/release/**'])
    .pipe(gulp.dest('./resources/public/'));
});

gulp.task('clean-shaed-assets', function () {
  return del(['./resources/public/cdn', './resources/rev-manifest.json']);
});

gulp.task('fix-source-map', function () {
  return sourceMapStream = gulp.src(['resources/public/js/out/main.js.map'], {base: './'})
    .pipe(jsonTransform(function(data) {
      data["sources"] = data["sources"].map(function(f) {
        return f.replace("\/", "/");
      });
      return data;
    }))
    .pipe(gulp.dest('./'));
});

gulp.task('save-git-sha-version', function (cb) {
  exec('git show --pretty=format:%H -q', function (err, stdout) {
    if (err) {
      cb(err);
    } else {
      fs.writeFile('resources/client_version.txt', stdout, function (err) {
        if (err) return cb(err);
        return cb();
      });
    }
  });
});

var shaedAssetSources = function () {
  return merge(gulp.src('resources/public/{js,css,images,fonts}/**')
               .pipe(gulpIgnore.exclude("*.map")),
               gulp.src('resources/public/js/out/main.js.map'));
};

gulp.task('rev-assets', function () {
  if (!argv.host) {
    throw "missing --host";
  }

  var revAll = new RevAll({
    prefix: "//" + argv.host + "/cdn/",
    dontSearchFile: ['.js']
  });

  return shaedAssetSources()
    .pipe(revAll.revision())
    .pipe(gulp.dest('resources/public/cdn'))
    .pipe(revAll.manifestFile())
    .pipe(gulp.dest('resources'));
});

gulp.task('fix-main-js-pointing-to-source-map', function (cb) {
  // because .js files are excluded from search and replace of sha-ed versions (so that
  // the js code doesn't become really wrong), we need to take special care to update
  // main.js to have the sha-ed version of the sourcemap in the file
  fs.readFile("resources/rev-manifest.json", 'utf8', function(err, data) {
    if (err) { return console.log(err); }

    var revManifest = JSON.parse(data),
        mainJsFilePath = "resources/public/cdn/" + revManifest["js/out/main.js"];

    fs.readFile(mainJsFilePath, 'utf8', function (err,data) {
      if (err) { return console.log(err); }
      var result = data.replace(/main\.js\.map/g, path.basename(revManifest["js/out/main.js.map"]));

      fs.writeFile(mainJsFilePath, result, 'utf8', function (err) {
        if (err) { return console.log(err); }
        cb();
      });
    });
  });
});

gulp.task('gzip', function () {
  return gulp.src('resources/public/cdn/**')
    .pipe(gzip({ append: false }))
    .pipe(gulp.dest('resources/public/cdn'));
});

gulp.task('cdn', function (cb) {
  runSequence('clean-shaed-assets', 'fix-source-map', 'rev-assets', 'fix-main-js-pointing-to-source-map', 'gzip', cb);
});

gulp.task('compile-assets', function(cb) {
  runSequence('css', 'minify-js', 'cljs-build', 'copy-release-assets', 'cdn', 'save-git-sha-version', cb);
});
