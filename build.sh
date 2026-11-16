#!/bin/sh
echo "Building RoyalArms..."
gradle build && echo "Done. Your jar is in build/libs/ (put it in your server's plugins folder)"
