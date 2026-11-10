package snippetsearcher.permissions.permission

import org.springframework.data.jpa.repository.JpaRepository

interface SnippetPermissionRepository : JpaRepository<SnippetPermission, SnippetPermissionId>
