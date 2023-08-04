fun main() {
  
  val vm = VM()
  
  vm.run(
    listOf(
      // {
      Opcode.StartBlock.ordinal,
      // let i0 = 0 / 1
      Opcode.BConst.ordinal, 1, Opcode.BConst.ordinal, 0, Opcode.Divide.ordinal, Opcode.StoreLocal.ordinal,
      // i0 = 10
      Opcode.IConst.ordinal, 10, Opcode.AssignLocal.ordinal, 0,
      // if(!i0) {
      Opcode.GetLocal.ordinal, 0, Opcode.JumpIfElse.ordinal, 10,
      // i0 = 100
      Opcode.IConst.ordinal, 100, Opcode.AssignLocal.ordinal, 0,
      // } else {
      Opcode.Jump.ordinal, 4,
      // i0 = 200
      Opcode.IConst.ordinal, 200, Opcode.AssignLocal.ordinal, 0,
      // }
      Opcode.EndBlock.ordinal,
      // #> 10 to 10 <#
      Opcode.IConst.ordinal, 10, Opcode.IConst.ordinal, 10, Opcode.SpawnObject.ordinal, 1,
      //value
      Opcode.IConst.ordinal, 5,
      //key
      Opcode.IConst.ordinal, 20, Opcode.AssignObject.ordinal, 0, Opcode.PopHeap.ordinal, 0,
      Opcode.DeclareFunction.ordinal, 0, 0,
      Opcode.CallFunction.ordinal, 0, 0,
      Opcode.PopHeap.ordinal, 0
    )
  )
  
  println("${vm.heap}")
}

enum class Opcode {
  BConst, IConst, FConst,
  Not, Add, Minus, Multiply, Divide,
  Pop,
  StoreLocal, GetLocal, AssignLocal,
  GetLocalAt, AssignLocalAt,
  StartBlock, EndBlock,
  Jump, JumpIf, JumpIfElse,
  SpawnObject, PopHeap, AssignObject, DeclareFunction, CallFunction
}

typealias Executable = List<Int>

class VM {
  //Anything local like numbers and such
  private val stack = mutableListOf<StackEntry>()
  
  //Variables
  private val frame = mutableListOf<StackEntry>()
  
  //Variable scope ranges
  private val frameRanges = mutableListOf(0..0)
  
  //Larger objects (objects, lists)
  val heap = mutableListOf<HeapEntry>()
  
  fun run(instructions: Executable) {
    val list = instructions.toMutableList()
    
    val jumpBy = { offset: Int -> for (i in 0..<offset) list.removeFirst() }
    
    while (list.isNotEmpty()) {
      if (list[0] > Opcode.entries.size) {
        throw Error("Not a valid opcode ${list[0]}")
      }
      
      when (Opcode.entries[list.removeFirst()]) {
        Opcode.BConst -> stack.add(BoolEntry(list.removeFirst() == 1))
        Opcode.IConst -> stack.add(IntEntry(list.removeFirst()))
        Opcode.FConst -> stack.add(FloatEntry(list.removeFirst().toFloat()))
        Opcode.Not -> stack.add(stack.removeLast().not())
        Opcode.Add -> stack.add(stack.removeLast() + stack.removeLast())
        Opcode.Minus -> stack.add(stack.removeLast() - stack.removeLast())
        Opcode.Divide -> stack.add(stack.removeLast() / stack.removeLast())
        Opcode.Multiply -> stack.add(stack.removeLast() * stack.removeLast())
        Opcode.Pop -> stack.removeLast()
        Opcode.StoreLocal -> {
          frameRanges[frameRanges.lastIndex] = frameRanges[frameRanges.lastIndex].let { it.first..(it.last + 1) }
          
          frame.add(stack.removeLast())
        }
        
        Opcode.GetLocal -> stack.add(frame[frame.lastIndex - list.removeFirst()])
        Opcode.AssignLocal -> frame[list.removeFirst()] = stack.removeLast()
        Opcode.GetLocalAt -> {
          val frameIdx = list.removeFirst()
          val at = list.removeFirst()
          
          val frameStart = frameRanges.reversed().subList(0, frameIdx).sumOf { it.count() }
          stack.add(frame[frame.size - (frameStart + at)])
        }
        
        Opcode.AssignLocalAt -> {
          val frameIdx = list.removeFirst()
          val at = list.removeFirst()
          
          val frameStart = frameRanges.reversed().subList(0, frameIdx).sumOf { it.count() }
          frame[frame.size - (frameStart + at)] = stack.removeLast()
        }
        
        Opcode.StartBlock -> frameRanges.add(0..0)
        Opcode.EndBlock -> frameRanges.removeLast().let { for (i in 0..<it.last) frame.removeLast() }
        Opcode.Jump -> jumpBy(list.removeFirst())
        Opcode.JumpIf -> {
          val cond = (stack.removeLast().not() as BoolEntry).value
          val offset = list.removeFirst()
          if (!cond) jumpBy(offset)
        }
        
        Opcode.JumpIfElse -> {
          val cond = (stack.removeLast().not() as BoolEntry).value
          val offset = list.removeFirst()
          if (cond) jumpBy(offset)
        }
        
        Opcode.SpawnObject -> {
          val obj = ObjectEntry()
          
          repeat(list.removeFirst()) {
            obj.entries[stack.removeLast()] = stack.removeLast()
          }
          
          heap.add(obj)
        }
        
        Opcode.PopHeap -> heap.removeAt(list.removeFirst())
        Opcode.AssignObject -> {
          val idx = list.removeFirst()
          
          if (heap[idx] !is ObjectEntry) {
            error("Index is not a valid object on the heap")
          }
          
          (heap[idx] as ObjectEntry).entries[stack.removeLast()] = stack.removeLast()
        }
        
        Opcode.CallFunction -> {
          val idx = list.removeFirst()
          
          val func = heap[idx]
          
          if (func !is FunctionEntry) {
            error("Index is not a valid function on the heap")
          }
          
          val params = list.removeFirst()
          val paramsList = mutableListOf<StackEntry>()
          
          for (i in 0..<params) {
            paramsList.add(stack.removeLast())
          }
          list.add(0, Opcode.StartBlock.ordinal)
          list.add(1, Opcode.EndBlock.ordinal)
          list.addAll(1, func.exec)
        }
        
        Opcode.DeclareFunction -> {
          val params = list.removeFirst()
          val blockLength = list.removeFirst()
          
          val code = mutableListOf<Number>()
          
          for (i in 0..<blockLength) {
            code.add(list.removeFirst())
          }
          
          @Suppress("UNCHECKED_CAST")
          heap.add(FunctionEntry(params, code as Executable))
        }
      }
    }
  }
}

sealed interface StackEntry {
  operator fun not(): StackEntry
  operator fun plus(other: StackEntry): StackEntry
  operator fun minus(other: StackEntry): StackEntry
  operator fun times(other: StackEntry): StackEntry
  operator fun div(other: StackEntry): StackEntry
}

data class BoolEntry(val value: Boolean) : StackEntry {
  override fun not(): BoolEntry = BoolEntry(!this.value)
  override fun plus(other: StackEntry): StackEntry = when (other) {
    is BoolEntry -> IntEntry((if (value) 1 else 0) + (if (other.value) 1 else 0))
    is IntEntry -> IntEntry(if (value) 1 else 0 + other.value)
    is FloatEntry -> FloatEntry((if (value) 1 else 0) + other.value)
  }
  
  override fun minus(other: StackEntry): StackEntry = when (other) {
    is BoolEntry -> IntEntry((if (value) 1 else 0) - (if (other.value) 1 else 0))
    is IntEntry -> IntEntry(if (value) 1 else 0 - other.value)
    is FloatEntry -> FloatEntry((if (value) 1 else 0) - other.value)
  }
  
  override fun times(other: StackEntry): StackEntry = when (other) {
    is BoolEntry -> IntEntry((if (value) 1 else 0) * (if (other.value) 1 else 0))
    is IntEntry -> IntEntry(if (value) 1 else 0 * other.value)
    is FloatEntry -> FloatEntry((if (value) 1 else 0) * other.value)
  }
  
  override fun div(other: StackEntry): StackEntry = when (other) {
    is BoolEntry -> IntEntry((if (value) 1 else 0) / (if (other.value) 1 else 0))
    is IntEntry -> IntEntry(if (value) 1 else 0 / other.value)
    is FloatEntry -> FloatEntry((if (value) 1 else 0) / other.value)
  }
}

data class IntEntry(val value: Int) : StackEntry {
  override fun not(): BoolEntry = BoolEntry(this.value == 0)
  override fun plus(other: StackEntry): StackEntry = when (other) {
    is BoolEntry -> IntEntry(value + (if (other.value) 1 else 0))
    is IntEntry -> IntEntry(value + other.value)
    is FloatEntry -> FloatEntry(value + other.value)
  }
  
  override fun minus(other: StackEntry): StackEntry = when (other) {
    is BoolEntry -> IntEntry(value - (if (other.value) 1 else 0))
    is IntEntry -> IntEntry(value - other.value)
    is FloatEntry -> FloatEntry(value - other.value)
  }
  
  override fun times(other: StackEntry): StackEntry = when (other) {
    is BoolEntry -> IntEntry(value * (if (other.value) 1 else 0))
    is IntEntry -> IntEntry(value * other.value)
    is FloatEntry -> FloatEntry(value * other.value)
  }
  
  override fun div(other: StackEntry): StackEntry = when (other) {
    is BoolEntry -> IntEntry(value / (if (other.value) 1 else 0))
    is IntEntry -> IntEntry(value / other.value)
    is FloatEntry -> FloatEntry(value / other.value)
  }
}

data class FloatEntry(val value: Float) : StackEntry {
  override fun not(): BoolEntry = BoolEntry(value == 0f)
  override fun plus(other: StackEntry): StackEntry = when (other) {
    is BoolEntry -> FloatEntry(value + (if (other.value) 1 else 0))
    is IntEntry -> FloatEntry(value + other.value)
    is FloatEntry -> FloatEntry(value + other.value)
  }
  
  override fun minus(other: StackEntry): StackEntry = when (other) {
    is BoolEntry -> FloatEntry(value - (if (other.value) 1 else 0))
    is IntEntry -> FloatEntry(value - other.value)
    is FloatEntry -> FloatEntry(value - other.value)
  }
  
  override fun times(other: StackEntry): StackEntry = when (other) {
    is BoolEntry -> FloatEntry(value * (if (other.value) 1 else 0))
    is IntEntry -> FloatEntry(value * other.value)
    is FloatEntry -> FloatEntry(value * other.value)
  }
  
  override fun div(other: StackEntry): StackEntry = when (other) {
    is BoolEntry -> FloatEntry(value / (if (other.value) 1 else 0))
    is IntEntry -> FloatEntry(value / other.value)
    is FloatEntry -> FloatEntry(value / other.value)
  }
}

sealed interface HeapEntry

data class ObjectEntry(val entries: MutableMap<StackEntry, StackEntry> = mutableMapOf()) : HeapEntry

data class FunctionEntry(val params: Int, val exec: Executable) : HeapEntry