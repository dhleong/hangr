#!/bin/bash

npm test \
    && echo "Running lein node tests" \
    && lein doo node node-test once \
    && echo "Running lein chrome tests" \
    && lein doo chrome chrome-test once
