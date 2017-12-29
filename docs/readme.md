
 To build the documentation locally do the following :

```
 > pip install sphinx sphinx-autobuild
 > cd docs
 > make html
```

 To build the other language(like Chinese version) documentation locally do the following :

```
 > pip install sphinx sphinx-autobuild
 > cd docs
 > make -e SPHINXOPTS="-D language='zh_CN'" html
```


 This will create the output in _build/html so on Mac you should be able to
 
```
> open _build/html/index.html
```
 which will open a browser with the rendered documentation
