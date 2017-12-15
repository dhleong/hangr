(ns hangr.node-runner
    (:require [doo.runner :refer-macros [doo-tests]]
              [hangr.util-test]
              [hangr.util.conversation-test]
              [hangr.util.parse-test]
              [hangr.views.widgets-test]))

(doo-tests 'hangr.util-test
           'hangr.util.conversation-test
           'hangr.util.parse-test
           'hangr.views.widgets-test)
