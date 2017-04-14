package org.reekwest.http.core.contract

abstract class Lens<in IN, OUT : Any, FINAL>(val meta: Meta, private val spec: LensSpec<IN, OUT>) {

    override fun toString(): String = "${if (meta.required) "Required" else "Optional"} ${meta.location} '${meta.name}'"

    operator fun invoke(target: IN): FINAL = try {
        convertIn(spec.locator.get(target, meta.name)?.let { it.map { it?.let(spec.deserialize) } })
    } catch (e: Missing) {
        throw e
    } catch (e: Exception) {
        throw Invalid(meta)
    }

    abstract internal fun convertIn(o: List<OUT?>?): FINAL
    abstract internal fun convertOut(o: FINAL): List<OUT>

    infix fun <R : IN> to(value: FINAL): (R) -> R = { invoke(value, it) }

    /**
     * The arguments to this method are in this specific order so we can partially apply several functions
     * and then fold them over a single target to modify.
     */
    @Suppress("UNCHECKED_CAST")
    operator fun <R : IN> invoke(value: FINAL, target: R): R =
        spec.locator.set(target, meta.name, convertOut(value).map(spec.serialize)) as R
}
