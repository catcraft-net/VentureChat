# README #

Read before making any changes or pull requests!

### What is this repository for?

VentureChat Paper plugin repository.

### How do I get set up?

Full setup and dependency guide is available here on SpigotMC: https://www.spigotmc.org/resources/venturechat.771/

In short, install `Vault` (and a compatible permission system), `ProtocolLib`, and `PlaceholderAPI` (`Player` and `Vault` extensions optional).

Version 4.0 targets Paper 26.2 and is built with Java 25. BungeeCord and Velocity plugin modes are no longer included.

Player settings are stored in `venturechat.db`. On the first 4.0 startup, existing `Players.yml` and `PlayerData/*.yml` files are placed in a verified ZIP backup and migrated automatically. The original loose files are removed only after both the backup and the new database pass verification. If migration cannot complete, VentureChat keeps the originals and safely falls back to them.

Player records are loaded one at a time during asynchronous pre-login. Changed records are queued to one background storage worker while the server is running, and shutdown waits at most 10 seconds before writing any remaining changes to a recovery journal.

To build and run the unit tests, run `mvn test`. The 50,000-player storage benchmark is opt-in with `mvn -Dventurechat.benchmark=true -Dtest=PlayerStorageBenchmarkTest test`.

### Contribution guidelines

* Follow the existing style conventions. I used the default Eclipse formatting styleset for the majority of the project.
* Follow general Java best practices and object oriented principles. There are examples of poor practices in this existing legacy codebase, but that doesn't mean new contributions should stoop to that level.
* Include JavaDoc for your contribution. Same as stated above, most of this legacy codebase isn't documented, but that doesn't mean new contributions should also be undocumented. Especially important when multiple people are working on a project.
* Include unit tests for stand alone methods. I'm not expecting full coverage for complex Spigot events, etc, but for formatting or other non Spigot related code, tests would be much appreciated.

### Who do I talk to?

* I'd recommend discussing any change before making it to avoid wasting your time and my time. Especially for a major change or if it's your first time contributing. I am extremely responsive on my Spigot discussion board (above link).

License:

Copyright (C) {2020}  {Austin Brolly}

```
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
```
