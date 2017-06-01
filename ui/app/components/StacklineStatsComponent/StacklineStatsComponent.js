import React, { Component } from 'react';
import styles from './StacklineStatsComponent.css';

// //Reference: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/is
// if (!Object.is) {
//   Object.is = function(x, y) {
//     // SameValue algorithm
//     if (x === y) { // Steps 1-5, 7-10
//       // Steps 6.b-6.e: +0 != -0
//       return x !== 0 || 1 / x === 1 / y;
//     } else {
//      // Step 6.a: NaN == NaN
//      return x !== x && y !== y;
//     }
//   };
// }

export default class StacklineStatsComponent extends Component {
  constructor (props) {
    super(props);
    this.state = {
    };
  }
  
  shouldComponentUpdate(nextProps, nextState) {
    //this.props.style can change because it is generated by react-virtualized
    // if(
    //   (this.props.nodestate !== nextProps.nodestate) || 
    //   (this.props.highlight !== nextProps.highlight) || 
    //   (this.props.listIdx !== nextProps.listIdx)
    //   //(!Object.is(this.props.style, nextProps.style))
    //   ) {
    //     if(this.props.stackline === "java.net.SocketOutputStream.socketWrite0") {
    //       console.log("yolo");
    //     }
    //   return true;
    // }
    // return false;
    return true;
  }

  render () {
    return (
       <div className={`${this.props.highlight ? styles.highlight : this.props.subdued && styles.subdued} ${styles.statContainer}`} style={this.props.style}>
          { this.props.samples ? (
            <div className={`${styles.pill} mdl-color-text--primary`}>
              <div className={styles.number}>{this.props.samples}</div>
              <div className={styles.percentage}>
                <div className={styles.shade} style={{ width: `${this.props.samplesPct}%` }}></div>
                {this.props.samplesPct}%
              </div>
            </div>
          ) : <div>&nbsp;</div> }
      </div>
    );
  }
}