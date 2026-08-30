package com.personal.sleepalarm.ui.library

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.LibraryItemType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Узел графа — элемент библиотеки. */
data class GraphNode(
    val id: Int,
    val title: String,
    val type: LibraryItemType,
    val coverPath: String?
)

/** Ребро графа — два элемента с общим тегом. */
data class GraphEdge(val a: Int, val b: Int)

internal fun buildLibraryGraphEdges(
    groups: Iterable<List<Int>>,
    validItemIds: Set<Int>
): List<GraphEdge> = groups.flatMap { ids ->
    val distinct = ids.asSequence().filter(validItemIds::contains).distinct().sorted().toList()
    buildList {
        for (i in distinct.indices) for (j in i + 1 until distinct.size) {
            add(GraphEdge(distinct[i], distinct[j]))
        }
    }
}.distinct()

/** Состояние графа. */
data class GraphState(
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<GraphEdge> = emptyList(),
    val positions: Map<Int, Offset> = emptyMap()
)

/**
 * ViewModel графа библиотеки.
 *
 * Строит узлы/рёбра из БД и гоняет force-directed симуляцию:
 * узлы отталкиваются, связанные притягиваются, всё стремится к центру.
 * Перетаскиваемый узел фиксируется на позиции пальца.
 */
class LibraryGraphViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application.applicationContext)
    private val dao = database.libraryDao()

    private val _state = MutableStateFlow(GraphState())
    val state: StateFlow<GraphState> = _state

    private val positions = mutableMapOf<Int, Offset>()
    private val velocity = mutableMapOf<Int, Offset>()
    private var draggedId: Int? = null

    init {
        // Структура графа: узлы из элементов, рёбра из общих тегов.
        viewModelScope.launch {
            combine(
                dao.observeItems(),
                dao.observeAllCrossRefs(),
                database.taskLibraryLinkDao().observeAll(),
                database.taskDao().observeAll()
            ) { items, refs, taskLinks, tasks ->
                val nodes = items.map {
                    GraphNode(it.id, it.title, it.type, it.coverUri)
                }

                // All pairs in a tag or a current task become material edges.
                val itemIds = nodes.map(GraphNode::id).toSet()
                val byTag = refs.groupBy({ it.tagId }, { it.itemId })
                val taskIds = tasks.map { it.id }.toSet()
                val byTask = taskLinks
                    .filter { it.taskId in taskIds }
                    .groupBy({ it.taskId }, { it.libraryItemId })
                val edges = buildLibraryGraphEdges(byTag.values + byTask.values, itemIds)

                GraphState(nodes = nodes, edges = edges)
            }.collect { struct ->
                // Инициализируем позиции новых узлов по кругу.
                val newIds = struct.nodes.map { it.id }.filter { it !in positions }
                newIds.forEachIndexed { idx, id ->
                    val angle = (idx.toFloat() / maxOf(newIds.size, 1)) * 2f * PI.toFloat() +
                            idx * 0.7f
                    positions[id] = Offset(
                        cos(angle) * INIT_RADIUS,
                        sin(angle) * INIT_RADIUS
                    )
                    velocity[id] = Offset.Zero
                }

                // Убираем позиции удалённых узлов.
                val alive = struct.nodes.map { it.id }.toSet()
                positions.keys.retainAll(alive)
                velocity.keys.retainAll(alive)

                _state.value = struct.copy(positions = positions.toMap())
            }
        }

        // Симуляция.
        viewModelScope.launch {
            while (isActive) {
                delay(SIM_STEP_MS)
                step()
            }
        }
    }

    // =================================================================
    // Force-directed шаг
    // =================================================================

    private fun step() {
        val nodes = _state.value.nodes
        val edges = _state.value.edges
        if (nodes.size < 2) return

        val force = mutableMapOf<Int, Offset>()
        nodes.forEach { force[it.id] = Offset.Zero }

        // Отталкивание всех пар.
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val a = nodes[i].id
                val b = nodes[j].id
                val pa = positions[a] ?: continue
                val pb = positions[b] ?: continue
                var d = pa - pb
                var dist = d.getDistance()
                if (dist < 1f) {
                    d = Offset(1f, 1f)
                    dist = 1.41f
                }
                val rep = REPULSION / (dist * dist)
                val dir = d / dist
                force[a] = (force[a] ?: Offset.Zero) + dir * rep
                force[b] = (force[b] ?: Offset.Zero) - dir * rep
            }
        }

        // Притяжение по рёбрам.
        edges.forEach { e ->
            val pa = positions[e.a] ?: return@forEach
            val pb = positions[e.b] ?: return@forEach
            val d = pb - pa
            val dist = d.getDistance()
            if (dist < 1f) return@forEach
            val att = ATTRACTION * dist
            val dir = d / dist
            force[e.a] = (force[e.a] ?: Offset.Zero) + dir * att
            force[e.b] = (force[e.b] ?: Offset.Zero) - dir * att
        }

        // Центрирование + интегрирование.
        nodes.forEach { n ->
            val id = n.id
            if (id == draggedId) {
                velocity[id] = Offset.Zero
                return@forEach
            }
            val p = positions[id] ?: return@forEach
            var f = force[id] ?: Offset.Zero
            f += (Offset.Zero - p) * CENTER_PULL

            var v = ((velocity[id] ?: Offset.Zero) + f) * DAMPING
            val speed = v.getDistance()
            if (speed > MAX_SPEED) v = v / speed * MAX_SPEED
            velocity[id] = v
            positions[id] = p + v
        }

        _state.value = _state.value.copy(positions = positions.toMap())
    }

    // =================================================================
    // Интерактив (вызываются из экрана)
    // =================================================================

    /** Узел под точкой (мировые координаты) или null. */
    fun nodeAt(world: Offset): Int? {
        var best: Int? = null
        var bestDist = NODE_RADIUS + 20f
        _state.value.positions.forEach { (id, p) ->
            val d = (p - world).getDistance()
            if (d < bestDist) {
                bestDist = d
                best = id
            }
        }
        return best
    }

    fun startDrag(id: Int) {
        draggedId = id
    }

    fun dragTo(id: Int, world: Offset) {
        positions[id] = world
        velocity[id] = Offset.Zero
    }

    fun endDrag() {
        draggedId = null
    }

    companion object {
        private const val SIM_STEP_MS = 16L
        private const val INIT_RADIUS = 320f
        private const val REPULSION = 26000f
        private const val ATTRACTION = 0.012f
        private const val CENTER_PULL = 0.006f
        private const val DAMPING = 0.85f
        private const val MAX_SPEED = 14f
        const val NODE_RADIUS = 28f
    }
}
