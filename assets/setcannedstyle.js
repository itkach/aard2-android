(function(styleTitle){
  // styleTitle is passed in (JSON-quoted by the caller).
  if (window.$styleSwitcher) {
    window.$styleSwitcher.setStyle(styleTitle);
  }
})
