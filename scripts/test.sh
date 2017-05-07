#!/bin/bash

npm test \
    && echo "Running lein node tests" \
    && lein doo node node-test once \
    && echo "Running lein phantom tests" \
    && lein doo phantom phantom-test once
