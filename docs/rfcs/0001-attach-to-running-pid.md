# New feature: Attaching to a running pid
The initialisation phase of an application isn't as interesting for inserting exceptions as the main
running phase because often errors during init are fatal and it's reasonable that you can't recover
from them. However, during the normal operation of high-reliability apps it should be possible to
(almost) always recover from exceptions.

## Non-goals
This obviates the need for any kind of delay parameter which would only insert faults after a
certain amount of time.
