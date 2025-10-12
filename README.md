# DreamPGs

BedWars Private Games plugin for Spigot 1.8.8 (Carbon-Spigot 1.8), integrating with BedWars1058 and LuckPerms. Provides party management and private game creation so you can run invite-only BedWars matches with your friends or staff.

- Platform: Spigot/Paper 1.8.8 (built against Spigot-API 1.8.8)
- Dependencies: BedWars1058 (API), LuckPerms (for permissions)
- Java: 8

## Features
- Party system with invite, accept, leave, disband, list, promote
- Create and manage private BedWars games tied to your party
- Integrates with BedWars1058 for arena join/start
- Fine-grained permission nodes for commands

## Requirements
- Java 8 (JDK 8)
- Spigot/Paper 1.8.8 (or Carbon-Spigot 1.8)
- BedWars1058 installed on the server
- LuckPerms (recommended) for permissions

## Installation
1. Ensure BedWars1058 and LuckPerms are installed on your server.
2. Drop the DreamPGs jar into your `plugins/` folder.
3. Start (or restart) the server.
4. A configuration file will be generated on first run if applicable.

## Building from source
You can build the plugin with Maven. The dependencies are marked as `provided` and are not shaded in.

```bash path=null start=null
# From the repository root
mvn -B -e -DskipTests clean package
# Output: target/DreamPGs-1.0.0.jar
```

## Commands
- /party
  - Usage: `/party <invite|accept|leave|disband|list|promote> [player]`
  - Aliases: `p`
  - Description: Manage your party.

- /pg
  - Usage: `/pg <create|start|disband|info|reload>`
  - Description: Create and manage a private game based on your party.

## Permissions
Top-level shortcuts:
- `PG.*` — All DreamPGs permissions (default: op)
- `PG.party.*` — All party permissions (default: op)
- `PG.pg.*` — All private game permissions (default: op)

Party permissions:
- `PG.party.base` (default: true)
- `PG.party.invite` (default: true)
- `PG.party.accept` (default: true)
- `PG.party.leave` (default: true)
- `PG.party.disband` (default: op)
- `PG.party.list` (default: true)
- `PG.party.promote` (default: op)

Private game permissions:
- `PG.pg.base` (default: true)
- `PG.pg.create` (default: op)
- `PG.pg.start` (default: op)
- `PG.pg.disband` (default: op)
- `PG.pg.info` (default: true)
- `PG.pg.reload` (default: op)

## Configuration
- A default configuration will be created on first run (if applicable). Consult in-game commands and comments for options. 
- Make sure BedWars1058 is configured with arenas suitable for private games.

## Compatibility
- Built with Spigot-API 1.8.8 and Java 8.
- Uses BedWars1058 API (2023.1) and LuckPerms API (5.4) as provided scope.

## Development
- Java 8, Maven 3.6+ recommended.
- Uses `maven-shade-plugin` for packaging (no dependencies shaded due to `provided`).

## License
Pending choice. If you’re unsure, MIT is a simple permissive default that allows broad use while retaining copyright.

## Acknowledgements
- SpigotMC / Spigot-API
- BedWars1058 (API)
- LuckPerms
