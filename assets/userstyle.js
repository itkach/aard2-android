(function(){
  if (!document.head) {
    return;
  }

  var styleElementId = '%s',
      title = '%s',
      css = '%s',
      existingElement = document.getElementById(styleElementId);

  if (existingElement && existingElement.title === title) {
    console.debug(
      'Element with title ' + title +
      ' and id ' + styleElementId +
      ' already exists');
    return;
  }

  if (existingElement) {
    console.debug(
      'Style element ' + existingElement.id +
      ' exists with title ' + existingElement.title +
      ' but title ' + title + ' is requested, removing');
    existingElement.remove();
  }

  if (window.$styleSwitcher) {
    console.debug('Clearing canned styles');
    window.$styleSwitcher.setStyle('');
  }

  var target = document.body,
      style = document.createElement('style');
  style.id = styleElementId;
  style.title = title;
  style.appendChild(document.createTextNode(css));
  target.appendChild(style);

})();
