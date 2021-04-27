/*
class ExperimentUnitTester(c: Experiment) extends PeekPokeTester(c) {
    
}


class ExperimentTester extends ChiselFlatSpec {
  "Experiment" should s"work very good (with firrtl)" in {
    Driver.execute(Array("--generate-vcd-output", "on", "--backend-name", "firrtl", "--target-dir", "test_run_dir/experiment_test"),
        () => new Experiment) {
      c => new ExperimentUnitTester(c)
    } should be (true)
  }
}
*/