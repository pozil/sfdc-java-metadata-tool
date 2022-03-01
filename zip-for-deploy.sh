#!/bin/sh
rm deploy.zip
zip -r deploy.zip unpackaged -x \*.DS_Store \*__MACOSX
