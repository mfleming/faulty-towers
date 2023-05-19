# faulty-towers

Faulty Towers is an exception injection agent. It triggers exceptions in your application to
randomly simulate them firing. Injecting exceptions in this way helps to build more robust software
because it forces you to ensure that you can recover from unexpected conditions on the unhappy code
paths.

## Ideas
### Delayed injection
Sometimes you don't want to insert `throw` statements as soon as the app starts up because you'll
hit a lot of init code that way. Instead, it seems more useful to only insert `throw` statements
once the app has been running for a while and you're in the normal operation loop.

Some kind of delay=30s parameter would be nice.

### History and Replay
With any kind of randomness it's always helpful to record a log of what order events occurred in so
that it's possible to *reproduce* that sequence at a later date. e.g. if faulty towers triggers an
exception it's helpful to be able to disable the randomness next time and continoulys trigger that
one exception over and over while you fix/improve the code to handle it.

### Filtering
Code bases are large and it would be helpful for users to be able to narrow down the packages where
we inject exceptions.

### Inject most executed code
If we combine the known places for exceptions with profiling data we then have the option of
inserting `throw` statements for the most heavily executed code. Alternatively, we could insert
exceptions for the *least* executed code where it's likely there are more bugs.

This actually seems like another case of filtering -- we have some list (most/least executed
methods) and we want to match up methods where we can inject exceptions with that list.

## FAQ

## Why not use byte-monkey?
- It doesn't support checked exceptions
- The project looks to be abandoned (hasn't had an update in years and the tests don't work)
