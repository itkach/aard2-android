(function(){
  if (!document.head) {
    return;
  }

  var styleElementId = '%s',
      title = '%s',
      css = '%s',
      existingElement = document.getElementById(styleElementId);

  if (existingElement && existingElement.title === title) {
    console.log('Element with title ' + title +
                ' and id ' + styleElementId +
                'already exists');
    return;
  }

  if (existingElement) {
    console.log('Style element exists, but with different title ' +
                existingElement.title + ', removing');
    existingElement.remove();
  }

  var head = document.head,
      style = document.createElement('style');
  style.id = styleElementId;
  style.appendChild(document.createTextNode(css));
  head.appendChild(style);

})();
