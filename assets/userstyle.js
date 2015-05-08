(function(){
  if (!document.head) {
    return;
  }

  var styleElementId = '%s',
      css = '%s',
      existingElement = document.getElementById(styleElementId);

  if (window.$styleSwitcher) {
    window.$styleSwitcher.setStyle('');
  }

  if (existingElement) {
    existingElement.parentNode.removeChild(existingElement);
  }

  var target = document.head,
      style = document.createElement('style');
  style.id = styleElementId;
  style.appendChild(document.createTextNode(css));
  target.appendChild(style);
})();
