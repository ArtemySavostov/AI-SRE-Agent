import { useState } from 'react'
import type { Project } from './api'

type Section = 'infrastructure' | 'monitoring' | 'logs'
export default function ProjectNavigation({
  projects,
  currentId,
  section,
  onSelect,
}: {
  projects: Project[]
  currentId?: string
  section: Section
  onSelect?: (id: string) => void
}) {
  return (
    <nav
      className="infra-project-tree"
      aria-label="Разделы проектов"
    >
      {projects.map((project) => (
        <ProjectBranch
          key={`${project.id}:${currentId === project.id}`}
          project={project}
          active={currentId === project.id}
          section={section}
          onSelect={onSelect}
        />
      ))}
    </nav>
  )
}
function ProjectBranch({
  project,
  active,
  section,
  onSelect,
}: {
  project: Project
  active: boolean
  section: Section
  onSelect?: (id: string) => void
}) {
  const [open, setOpen] = useState(active)
  return (
    <div className={active ? 'infra-project-branch active' : 'infra-project-branch'}>
      <button
        className="infra-project-toggle"
        aria-expanded={open}
        aria-controls={`project-nav-${project.id}`}
        onClick={() => setOpen((value) => !value)}
      >
        <span aria-hidden="true">{open ? '▾' : '▸'}</span>
        <span>{project.name}</span>
      </button>
      {open && (
        <div
          id={`project-nav-${project.id}`}
          className="infra-project-children"
        >
          {(
            [
              ['infrastructure', 'Серверы'],
              ['monitoring', 'Мониторинг'],
              ['logs', 'Логи'],
            ] as const
          ).map(([route, title]) => (
            <a
              key={route}
              href={`/${route}?${new URLSearchParams({ project: project.id })}`}
              aria-current={active && section === route ? 'page' : undefined}
              onClick={(event) => {
                if (
                  route === 'infrastructure' &&
                  onSelect &&
                  !event.ctrlKey &&
                  !event.metaKey &&
                  !event.shiftKey &&
                  !event.altKey
                ) {
                  event.preventDefault()
                  onSelect(project.id)
                }
              }}
            >
              {title}
            </a>
          ))}
        </div>
      )}
    </div>
  )
}
