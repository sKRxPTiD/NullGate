# NullGate 0.2.0 RC1 promotion record — 2026-09-28

## Result

The audited PiXi private 0.2.0 RC1 bundle and its checksum sidecar were copied
from local staging into the authoritative release directory:

`/mnt/TerraDrive/Null Protocol/Apps/NullGate/releases`

This promotes the reviewed candidate artifact for preservation. It does not
rename RC1 as a final public release, install software, alter PiXi, create a Git
tag or publish anything remotely.

## Promoted identities

```text
0d6164f1ae5401bbea33ed935e068523ac157c9df97446e9b1825d03be9d88da  NullGate-PiXi-private-0.2.0-rc1-2026-09-28.zip
d60233d0596b3b66fe4c5f8802a148eef48d3135fd882f19e17fa551ba1ca0e2  NullGate-PiXi-private-0.2.0-rc1-2026-09-28.zip.sha256
```

The checksum sidecar verifies the promoted ZIP, and both promoted files compare
byte-for-byte with their audited staging copies.

## Preserved 0.1.0 baseline

The existing verified private 0.1.0 baseline was not overwritten or modified:

```text
bc5a9e52570eccc99109e9032101845de9402438845ae8eb3a5c4a98fa4d6125  NullGate-PiXi-private-2026-09-26.zip
d118571d4dc33ffb5e9e94fcd7e2951f968364d6dced54a34c24232f8def072a  NullGate-PiXi-private-2026-09-26.zip.sha256
```

## Source and operating state

- Candidate source commit: `fb340887d685169bbda98e64f3e681b9984ea008`
- Candidate staging audit: `CANDIDATE_STAGE_2026-09-28.md`
- Rooted debugging: off
- Git push: not performed
- Public release: not performed
- Protected malformed untracked filename: untouched
