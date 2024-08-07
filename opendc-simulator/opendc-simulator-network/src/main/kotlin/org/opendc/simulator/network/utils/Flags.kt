@file:OptIn(InternalUse::class, InternalUse::class)

package org.opendc.simulator.network.utils


public interface Flags<T> {
    @InternalUse
    public val value: Int

    public fun <P> ifSet(flag: Flag<T>, dflt: P? = null, block: () -> P): P? =
        if (this.value and flag.value != 0)
            block()
        else dflt

    public operator fun plus(other: Flags<T>): Flags<T> =
        MultiFlags(this.value or other.value)

    public operator fun minus(other: Flags<T>): Flags<T> =
        MultiFlags(this.value and other.value.inv())

    public companion object {
        public fun <T> all(): Flags<T> = MultiFlags(-1)
    }
}

@JvmInline
private value class MultiFlags<T> (
    override val value: Int
): Flags<T>

@JvmInline
public value class Flag<T>(override val value: Int): Flags<T> {
    init {
        // Check power of 2 => 1 bit set.
        require(value != 0 && (value and (value-1)) == 0)
    }
}
