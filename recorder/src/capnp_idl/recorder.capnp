@0xb8c7ea0e25cb2b20;

struct StackSample {
  startOffsetMicros @0 :UInt32;
  threadId @1 :Int64;
  frames @2 :List(Frame);
}

struct Frame {
  methodId @0 :Int64;
  bci @1 :Int32;
  lineNo @2 :Int32;
}


