(function(){
  var elementId = '%s';
  var existingElement = document.getElementById(elementId);
  if (existingElement) {
    console.debug('Removing style element ' +
                  elementId + ': ' + existingElement);
    existingElement.remove();
  }
})();
