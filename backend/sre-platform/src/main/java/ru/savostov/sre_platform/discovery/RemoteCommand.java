package ru.savostov.sre_platform.discovery;

/** Fixed read-only commands; request data is never interpolated into shell code. */
enum RemoteCommand {
    HOST("""
            set -eu
            printf 'hostname=%s\n' "$(hostname)"
            printf 'kernel=%s\n' "$(uname -r)"
            printf 'architecture=%s\n' "$(uname -m)"
            printf 'cpuCount=%s\n' "$(getconf _NPROCESSORS_ONLN)"
            awk '/^MemTotal:/ {printf "memoryBytes=%.0f\\n", $2 * 1024}' /proc/meminfo
            awk '{print "uptimeSeconds=" $1}' /proc/uptime
            if [ -r /etc/os-release ]; then
              . /etc/os-release
              printf 'operatingSystem=%s\n' "${PRETTY_NAME:-Linux}"
            else
              printf 'operatingSystem=Linux\n'
            fi
            """),
    DOCKER("docker info --format '{\"version\":{{json .ServerVersion}},\"name\":{{json .Name}},\"storageDriver\":{{json .Driver}},\"containers\":{{.Containers}},\"running\":{{.ContainersRunning}},\"stopped\":{{.ContainersStopped}}}'"),
    CONTAINERS("""
            set -eu
            ids=$(docker ps -aq --no-trunc)
            if [ -n "$ids" ]; then
              set -- $ids
              [ "$#" -le 1000 ] || exit 42
              docker inspect --type container --format '{"id":{{json .Id}},"name":{{json .Name}},"image":{{json .Config.Image}},"state":{{json .State.Status}},"health":{{with (index .State "Health")}}{{json .Status}}{{else}}null{{end}},"composeProject":{{json (index .Config.Labels "com.docker.compose.project")}},"composeService":{{json (index .Config.Labels "com.docker.compose.service")}},"startedAt":{{json .State.StartedAt}},"ports":{{json .NetworkSettings.Ports}},"networks":{{json .NetworkSettings.Networks}}}' "$@"
            fi
            """);

    final String script;
    RemoteCommand(String script) { this.script = script; }
}
