(function(){
  if (!document.head) {
    return;
  }

  var styleElementId = '%s',
      css = '%s',
      existingElement = document.getElementById(styleElementId);

  if (window.$styleSwitcher) {
    console.debug('Clearing canned styles');
    window.$styleSwitcher.setStyle('');
  }

  if (existingElement) {
    existingElement.remove();
  }

  var target = document.head,
      style = document.createElement('style');
  style.id = styleElementId;
  style.appendChild(document.createTextNode(css));
  target.appendChild(style);
})();
