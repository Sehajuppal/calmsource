module.exports = function (api) {
  api.cache(true);
  return {
    // `lazyImports` makes Metro emit lazy `require` calls so modules only
    // evaluate on first use. Big startup win on low-end Android — the JS
    // bundle still ships, but parse/exec is spread across navigations
    // instead of frozen on the splash screen.
    presets: [["babel-preset-expo", { lazyImports: true }]],
  };
};
