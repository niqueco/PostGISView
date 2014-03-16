PostGISView
===========

This simple tool can be used to browse a PostGIS database.

Features
--------

 * Can show multiple rows in the same map.
 * It sends shapes to the server for validity check and highlights problem areas.
 * Intuitive panning and zooming.
 * OpenStreetMap tiles drawn below the database data.

Building
--------

To build this you'll need [Java Geodesy Library for GPS](http://www.gavaghan.org/blog/free-source-code/geodesy-library-vincentys-formula-java/).
You'll need to make some minor modifications to it (making some methods static).
 
Author
------

Nicolás Lichtmaier <nico.lichtmaier@gmail.com>
