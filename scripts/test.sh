#!/bin/bash

npm test && echo "Running lein tests" && lein doo node once
