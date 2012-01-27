Android SMS Relay
=================

This is a simple SMS relayer.  It essentially turns your Android phone into an SMS modem that will relay messages to an HTTP server of your choice and vice versa.

We use this quite a bit in our work, so we thought we'd open it up to the world.

Things that make it different than other solutions:

* we are backed by a database for persistence, we think that makes it less likely a message will be dropped in the case of power outages, reboots, or other issues
* we have a prettier message UI than the other guys

This is our first release, so there's still a lot we still have in store.  Pull requests happily accepted.